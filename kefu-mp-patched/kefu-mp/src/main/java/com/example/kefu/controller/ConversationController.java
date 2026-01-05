package com.example.kefu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kefu.common.ApiResp;
import com.example.kefu.dto.ConversationListItem;
import com.example.kefu.entity.Conversation;
import com.example.kefu.entity.Message;
import com.example.kefu.entity.User;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.ConversationService;
import com.example.kefu.service.CustomerService;
import com.example.kefu.service.MessageService;
import com.example.kefu.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final CustomerService customerService;
    private final UserService userService; // ⬅️ 新增：用于拿“可见代理ID集合”
    private final UsersController usersController; // ⬅️ 新增：用于拿“可见代理ID集合”

    @GetMapping
    public ApiResp<Map<String, Object>> list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) Long beforeAtTs, // 毫秒
            @RequestParam(required = false) Long beforeId
    ) {
        // ===== 0) 鉴权与可见代理集合（保留你现有逻辑） =====
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");
        User me = userService.getById(ctx.getUserId());
        if (me == null) return ApiResp.fail("UNAUTHORIZED");
        String roleUp = (me.getRole() == null ? "" : me.getRole().toUpperCase());

        Set<Long> allowedAgentIds = null;
        switch (roleUp) {
            case "ROOT" -> allowedAgentIds = null;
            case "SUPER_AGENT", "AGENT" -> {
                List<Long> visible = usersController.buildVisibleAgentIds(me.getId());
                allowedAgentIds = new HashSet<>(visible);
                if (allowedAgentIds.isEmpty()) {
                    Map<String,Object> empty = Map.of(
                            "records", List.of(),
                            "hasMore", false,
                            "next", null,
                            "serverTime", System.currentTimeMillis()
                    );
                    return ApiResp.ok(empty);
                }
            }
            case "CS" -> {
                Long agentId = me.getAgentId();
                if (agentId == null) return ApiResp.fail("CS账号未绑定上级代理(agentId)");
                allowedAgentIds = Collections.singleton(agentId);
            }
            default -> { return ApiResp.fail("FORBIDDEN_ROLE"); }
        }

        // 安全限制
        limit = Math.max(1, Math.min(200, limit));

        // ===== 1) 构建查询（游标 + 稳定二键排序）=====
        LambdaQueryWrapper<Conversation> q = new LambdaQueryWrapper<>();
        if (allowedAgentIds != null) q.in(Conversation::getAgentId, allowedAgentIds);

        // 游标： (last_message_at, id) 组成严格“更老”的窗口
        if (beforeAtTs != null && beforeId != null) {
            LocalDateTime beforeAt =
                    java.time.Instant.ofEpochMilli(beforeAtTs)
                            .atZone(java.time.ZoneOffset.UTC)   // 你的时间列如果就是UTC，保留UTC；若是本地时区请换 ZoneId.systemDefault()
                            .toLocalDateTime();
            q.and(w -> w.lt(Conversation::getLastMessageAt, beforeAt)
                    .or().eq(Conversation::getLastMessageAt, beforeAt).lt(Conversation::getId, beforeId));
        }

        q.orderByDesc(Conversation::getLastMessageAt)
                .orderByDesc(Conversation::getId)
                .last("LIMIT " + limit);

        List<Conversation> rows = conversationService.list(q);
        if (rows.isEmpty()) {
            Map<String,Object> payload = Map.of(
                    "records", List.of(),
                    "hasMore", false,
                    "next", null,
                    "serverTime", System.currentTimeMillis()
            );
            return ApiResp.ok(payload);
        }

        // ===== 2) 组装 DTO（沿用你原来的逐条补充；建议后续做批量优化）=====
        List<ConversationListItem> items = new ArrayList<>(rows.size());
        for (Conversation c : rows) {
            ConversationListItem dto = new ConversationListItem();
            dto.setId(c.getId());
            dto.setAgentId(c.getAgentId());
            dto.setCustomerId(c.getCustomerId());
            dto.setCsUserId(c.getCsUserId());
            dto.setChannel(c.getChannel());
            dto.setStatus(c.getStatus());
            dto.setStartedAt(c.getStartedAt());
            dto.setClosedAt(c.getClosedAt());
            dto.setLastMessageAt(c.getLastMessageAt());

            // 最后一条消息（保留你原来写法）
            Message last = messageService.lambdaQuery()
                    .eq(Message::getConversationId, c.getId())
                    .orderByDesc(Message::getId)
                    .last("limit 1")
                    .one();
            if (last != null) {
                dto.setLastMessageId(last.getId());
                dto.setLastMessageSenderType(last.getSenderType());
                dto.setLastMessageContentType(last.getContentType());
                dto.setLastMessageContent(last.getContent());
                dto.setLastMessageIsRevoked(last.getIsRevoked());
                dto.setLastMessageCreatedAt(last.getCreatedAt());
            }

            long unread = messageService.lambdaQuery()
                    .eq(Message::getConversationId, c.getId())
                    .eq(Message::getSenderType, 1)
                    .eq(Message::getIsRevoked, 0)
                    .eq(Message::getIsRead, 0)
                    .count();
            dto.setUnreadCount((int) unread);

            var customer = customerService.getById(c.getCustomerId());
            if (customer != null) {
                dto.setCustomerNote(customer.getNote());
                dto.setRegion(customer.getRegion());
            }
            items.add(dto);
        }

        // ===== 3) next 游标 =====
        Conversation tail = rows.get(rows.size() - 1);
        Long nextAtTs = (tail.getLastMessageAt() == null) ? null :
                tail.getLastMessageAt()
                        .atZone(java.time.ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli();
        Map<String, Object> next = (rows.size() == limit)
                ? Map.of("beforeAtTs", nextAtTs, "beforeId", tail.getId())
                : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("records", items);
        payload.put("hasMore", rows.size() == limit);
        payload.put("next", next);
        payload.put("serverTime", System.currentTimeMillis());

        return ApiResp.ok(payload);
    }

    @PostMapping
    public ApiResp<Conversation> create(@RequestBody Conversation conv) {
        conv.setStartedAt(LocalDateTime.now());
        conv.setLastMessageAt(LocalDateTime.now());
        conversationService.save(conv);
        return ApiResp.ok(conv);
    }

    @PostMapping("/{id}/mark-read")
    public ApiResp<Boolean> markRead(@PathVariable Long id, @RequestParam String side) {
        Conversation c = conversationService.getById(id);
        if (c == null) return ApiResp.fail("conversation not found");
        if ("CS".equalsIgnoreCase(side)) {
            c.setCsLastReadAt(LocalDateTime.now());
        } else {
            c.setCustomerLastReadAt(LocalDateTime.now());
        }
        conversationService.updateById(c);
        return ApiResp.ok(true);
    }
}
