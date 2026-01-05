package com.example.kefu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kefu.common.ApiResp;
import com.example.kefu.entity.Conversation;
import com.example.kefu.entity.Customer;
import com.example.kefu.entity.Message;
import com.example.kefu.entity.User;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.ConversationService;
import com.example.kefu.service.CustomerService;
import com.example.kefu.service.MessageService;
import com.example.kefu.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final UserService userService;

    // ========================= 确保客户 + 会话（前端首次进入调用） =========================
    @PostMapping("/ensure")
    @Transactional(rollbackFor = Exception.class)
    public ApiResp<EnsureResp> ensure(@RequestBody(required = false) EnsureReq req,
                                      HttpServletRequest http) {

        // 0) 读取 code（body 优先，其次 query）
        String code = (req != null && StringUtils.hasText(req.getCode()))
                ? req.getCode()
                : http.getParameter("code");

        if (!StringUtils.hasText(code)) {
            return ApiResp.fail("缺少 code");
        }

        // 1) 通过 code 定位代理用户（启用状态）
        User agentUser = userService.lambdaQuery()
                .eq(User::getCode, code)
                .eq(User::getStatus, 1)
                .last("limit 1")
                .one();
        if (agentUser == null) {
            return ApiResp.fail("code is null");
        }
        Long agentId = agentUser.getId();
        if (agentId == null) {
            return ApiResp.fail("agentId is null");
        }

        // 2) 识别 visitorToken（优先前端；否则随机，不用IP）
        String ip = Optional.ofNullable(http.getRemoteAddr()).orElse("未知IP");
        String token = null;
        if (req != null && StringUtils.hasText(req.getVisitorToken())) {
            token = req.getVisitorToken().trim();
        }
        if (!StringUtils.hasText(token)) {
            String q1 = http.getParameter("visitorToken");
            String q2 = http.getParameter("vt");
            if (StringUtils.hasText(q1)) token = q1.trim();
            else if (StringUtils.hasText(q2)) token = q2.trim();
        }
        if (!StringUtils.hasText(token)) {
            token = "v_" + java.util.UUID.randomUUID();
        }

        // 3) 查或建客户（带唯一键幂等）
        Customer c = customerService.lambdaQuery()
                .eq(Customer::getVisitorToken, token)
                .eq(Customer::getAgentId, agentId)
                .last("limit 1")
                .one();

        boolean isNewCustomer = false;

        if (c == null) {
            Customer nc = new Customer();
            nc.setVisitorToken(token);
            nc.setName((req != null && StringUtils.hasText(req.getName())) ? req.getName() : "游客" + tokenSuffix(token));
            nc.setAgentId(agentId);
            nc.setFirstIp(ip);
            try {
                customerService.save(nc);
                c = nc;
                isNewCustomer = true;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发下可能已被别的请求创建：回查
                c = customerService.lambdaQuery()
                        .eq(Customer::getVisitorToken, token)
                        .eq(Customer::getAgentId, agentId)
                        .last("limit 1")
                        .one();
            }
        } else if (c.getAgentId() == null) {
            c.setAgentId(agentId);
            customerService.updateById(c);
        }

        getIpAddr(ip, c);

        // 4) 查或建“打开中的”会话
        Conversation conv = conversationService.lambdaQuery()
                .eq(Conversation::getCustomerId, c.getId())
                .eq(Conversation::getAgentId, c.getAgentId())   // ⭐ 关键：限定到该代理
                .eq(Conversation::getStatus, 1)
                .orderByDesc(Conversation::getId)
                .last("limit 1")
                .one();

        if (conv == null) {
            conv = new Conversation();
            conv.setCustomerId(c.getId());
            conv.setAgentId(c.getAgentId());    // 必然是当前代理
            conv.setChannel("web");
            conv.setStatus(1);
            conv.setStartedAt(LocalDateTime.now());
            conv.setLastMessageAt(LocalDateTime.now());
            conversationService.save(conv);
        } else if (conv.getAgentId() == null) {
            conv.setAgentId(c.getAgentId());
            conversationService.updateById(conv);
        }

        // ⭐ 新用户首访：读取 agentUser.autoReply（逗号分隔），逐条（最多3条）发送
        if (isNewCustomer) {
            String autoReply = agentUser.getAutoReply();
            if (StringUtils.hasText(autoReply)) {
                List<String> replies = Arrays.stream(autoReply.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .limit(3)   // 最多三条
                        .collect(Collectors.toList());

                for (String content : replies) {
                    Message m = new Message();
                    m.setConversationId(conv.getId());
                    m.setAgentId(conv.getAgentId());
                    m.setSenderType(2);         // 或者固定你们系统里客服的 senderType
                    m.setSenderUserId(agentUser.getId()); // 发件人就是该代理用户
                    m.setContentType(1);         // 如果都是文本，就写死 text
                    m.setContent(content);
                    m.setCreatedAt(LocalDateTime.now());
                    m.setIsRevoked(0);
                    messageService.save(m);
                }
            }
        }


        // 5) 组装响应（把 user.chatTitle / user.chatLogo 也带回去）
        EnsureResp out = new EnsureResp();
        out.setCustomerId(c.getId());
        out.setConversationId(conv.getId());
        out.setVisitorToken(token);
        out.setBrandName(agentUser.getBrandName());
        out.setBrandLogo(agentUser.getBrandLogo());
        out.setBrandIntro(agentUser.getBrandIntro());
        out.setCsName(agentUser.getCsName());
        out.setCsLogo(agentUser.getCsLogo());
        out.setVisitorToken(token);

        return ApiResp.ok(out);
    }

    private void getIpAddr(String ip, Customer c) {
// —— 异步查 IP 地理信息，更新 customer.region（国家·城市，中文）——
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                String ipStr = ip;
                if (ipStr == null || ipStr.isBlank() || "127.0.0.1".equals(ipStr) || "0.0.0.0".equals(ipStr)) {
                    return;
                }

                java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(2))
                        .build();

                // ⭐ 加 lang=zh-CN 返回中文
                String url = "http://ip-api.com/json/" +
                        java.net.URLEncoder.encode(ipStr, java.nio.charset.StandardCharsets.UTF_8) +
                        "?fields=status,country,city&lang=zh-CN";

                java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(3))
                        .GET()
                        .build();

                java.net.http.HttpResponse<String> httpResp =
                        client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());

                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(httpResp.body());

                String status = node.path("status").asText("");
                if (!"success".equalsIgnoreCase(status)) return;

                String country = node.path("country").asText("");
                String city = node.path("city").asText("");
                StringBuilder sb = new StringBuilder();
                if (country != null && !country.isBlank()) sb.append(country);
                if (city != null && !city.isBlank()) {
                    if (sb.length() > 0) sb.append('·');
                    sb.append(city);
                }
                String region = sb.toString();
                if (region.isBlank()) return;

                // 更新客户的 region 字段
                Customer patch = new Customer();
                patch.setId(c.getId());
                patch.setRegion(region);
                customerService.updateById(patch);

            } catch (Exception ignore) {
                // 静默失败，不影响主流程
            }
        });

    }

// ========================= 原有接口（保留） =========================

    @GetMapping
    public ApiResp<Page<Customer>> list(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) String keyword) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        final Long uid = ctx.getUserId();
        final Long agentIdOfCS = ctx.getAgentId();                 // CS 用
        final String role = (ctx.getRole() == null ? "" : ctx.getRole().toUpperCase());

        LambdaQueryWrapper<Customer> q = new LambdaQueryWrapper<>();

        // 关键词搜索（和你之前一致）
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            q.and(w -> w.like(Customer::getName, keyword)
                    .or().like(Customer::getEmail, keyword)
                    .or().like(Customer::getVisitorToken, keyword));
        }

        // 权限过滤
        switch (role) {
            case "ROOT":
                // 不加 agentId 过滤 = 查全部
                break;

            case "SUPER_AGENT": {
                // 查自己 + 直属代理（role=AGENT 且 agent_id = 自己）
                List<Long> agentIds = new java.util.ArrayList<>();
                agentIds.add(uid); // 自己
                List<User> children = userService.list(
                        new LambdaQueryWrapper<User>()
                                .select(User::getId)
                                .eq(User::getRole, "AGENT")
                                .eq(User::getAgentId, uid)
                );
                for (User u : children) agentIds.add(u.getId());
                q.in(Customer::getAgentId, agentIds);
                break;
            }

            case "AGENT":
                // 只看自己的客户
                q.eq(Customer::getAgentId, uid);
                break;

            case "CS":
                // 只看“所属代理”的客户（AuthContext 里带的上级代理ID）
                if (agentIdOfCS == null) {
                    // 没有配置上级代理就查不到任何数据（也可以选择返回 403）
                    q.eq(Customer::getAgentId, -1L);
                } else {
                    q.eq(Customer::getAgentId, agentIdOfCS);
                }
                break;

            default:
                return ApiResp.fail("FORBIDDEN");
        }

        // 排序
        q.orderByDesc(Customer::getUpdatedAt)
                .orderByDesc(Customer::getId);

        Page<Customer> p = customerService.page(Page.of(page, size), q);
        return ApiResp.ok(p);
    }

    @PostMapping
    public ApiResp<Customer> create(@RequestBody Customer c) {
        if (c.getAgentId() == null) {
            AuthContext ctx = HeaderAuthInterceptor.CTX.get();
            Long agentId = (ctx != null && ctx.getAgentId() != null) ? ctx.getAgentId() : null;
            if (agentId == null) return ApiResp.fail("NO_VALID_AGENT");
            c.setAgentId(agentId);
        }
        customerService.save(c);
        return ApiResp.ok(c);
    }

    @PutMapping("/{id}/note")
    public ApiResp<Customer> updateNote(@PathVariable Long id, @RequestBody NoteReq req) {
        if (req == null || req.content == null) {
            return ApiResp.fail("content required");
        }
        Customer c = customerService.getById(id);
        if (c == null) return ApiResp.fail("customer not found");

        String val = req.content.trim();
        c.setNote(val);
        customerService.updateById(c);
        return ApiResp.ok(c);
    }

    @GetMapping("/{id}")
    public ApiResp<Customer> get(@PathVariable @NotNull Long id) {
        return ApiResp.ok(customerService.getById(id));
    }

// ========================= DTO & 工具方法 =========================

    @Data
    public static class NoteReq {
        public String content;
    }

    @Data
    public static class EnsureReq {
        private String code;         // 推广/绑定码
        private String visitorToken; // 可选：前端自带访客标识；不传则使用 IP
        private String name;         // 可选：新建客户时的显示名
        private Object meta;         // 可选：预留字段
    }

    @Data
    public static class EnsureResp {
        private Long customerId;
        private Long conversationId;
        private String visitorToken;
        private String brandName;
        private String brandLogo;
        private String brandIntro;
        private String csName;
        private String csLogo;
    }

    /**
     * 生成游客名后缀
     */
    private static String tokenSuffix(String token) {
        if (!StringUtils.hasText(token)) return "";
        String t = token.replaceAll("[^0-9A-Za-z]+", "");
        if (t.length() <= 6) return t;
        return t.substring(t.length() - 6);
    }
}
