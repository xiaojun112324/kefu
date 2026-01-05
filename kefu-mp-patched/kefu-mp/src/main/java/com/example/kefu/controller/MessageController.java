package com.example.kefu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kefu.common.ApiResp;
import com.example.kefu.dto.FileInfo;
import com.example.kefu.dto.RevokeMessageReq;
import com.example.kefu.dto.SendMessageReq;
import com.example.kefu.entity.Conversation;
import com.example.kefu.entity.Message;
import com.example.kefu.entity.User;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.ConversationService;
import com.example.kefu.service.MessageService;
import com.example.kefu.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    // ====== 内容类型与路径常量 ======
    // 你的系统常量 (保持与前端一致)
    private static final int CT_TEXT = 1, CT_IMAGE = 2, CT_AUDIO = 3, CT_FILE = 4, CT_VIDEO = 5, CT_RICH = 6;

    /** 本地根目录 & URL 前缀（Nginx/CF 反代后依旧保持 /file/） */
    private static final String ROOT_DIR   = "/www/wwwroot/file";
    private static final String URL_PREFIX = "/file"; // 拼接为 /file/{conversationId}/{name}

    /** 强校验：会话必须存在；不存在则返回 null，由调用方返回 fail */
    private Conversation mustLoadConv(Long conversationId) {
        if (conversationId == null || conversationId <= 0) return null;
        return conversationService.getById(conversationId);
    }

    @GetMapping
    public ApiResp<Map<String, Object>> list(@RequestParam Long conversationId,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "50") long size,
                                             @RequestParam(required = false) Long afterId,
                                             @RequestParam(required = false, defaultValue = "CS") String side,
                                             @RequestParam(required = false) Long sinceRevokedAt) {
        // 0) 会话存在性校验（只拿一次，避免后面 N 次失败）
        Conversation conv = mustLoadConv(conversationId);
        if (conv == null) {
            return ApiResp.fail("conversation not found: call /customers/ensure first");
        }

        // 1) 分页查消息：只取「用得上的列」，按 id 降序
        LambdaQueryWrapper<Message> q = new LambdaQueryWrapper<>();
        q.eq(Message::getConversationId, conversationId);
        if (afterId != null && afterId > 0) q.gt(Message::getId, afterId);
        q.select(
                Message::getId,
                Message::getConversationId,
                Message::getSenderType,
                Message::getIsRevoked,
                Message::getIsRead,
                Message::getContentType,
                Message::getContent,
                Message::getCreatedAt
        );
        q.orderByDesc(Message::getId);

        Page<Message> result = messageService.page(Page.of(page, size), q);
        List<Message> recs = result.getRecords();

        // 2) 标记已读（一次批量 UPDATE）
        final int targetSenderType = "USER".equalsIgnoreCase(side) ? 2 : 1;
        if (recs != null && !recs.isEmpty()) {
            // 列表现在是倒序 (100, 99, 98)，所以最大的 ID 在第 0 个
            long maxId = recs.get(0).getId();

            messageService.update(
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Message>()
                            .eq(Message::getConversationId, conversationId)
                            .eq(Message::getSenderType, targetSenderType)
                            .eq(Message::getIsRevoked, 0)
                            .eq(Message::getIsRead, 0)
                            .le(Message::getId, maxId)
                            .set(Message::getIsRead, 1)
            );

            // 同步内存对象，前端立即看到 isRead=1
            for (int i = recs.size() - 1; i >= 0; i--) {
                Message m = recs.get(i);
                if (m.getSenderType() != null
                        && m.getSenderType() == targetSenderType
                        && (m.getIsRevoked() == null || m.getIsRevoked() == 0)) {
                    m.setIsRead(1);
                }
            }
        }

        // 3) 一次性聚合出两侧已读的最大 id（单条 SQL，条件聚合）
        Long customerReadMaxId = 0L;
        Long csReadMaxId = 0L;
        {
            QueryWrapper<Message> agg = new QueryWrapper<Message>()
                    .select(
                            "MAX(CASE WHEN sender_type = 2 AND is_read = 1 THEN id END) AS customerReadMaxId",
                            "MAX(CASE WHEN sender_type = 1 AND is_read = 1 THEN id END) AS csReadMaxId"
                    )
                    .eq("conversation_id", conversationId);

            List<Map<String, Object>> maps = messageService.getBaseMapper().selectMaps(agg);

            // —— 极简稳妥判空：既防止 maps 为空，也防止 maps=[null] 的情况 ——
            if (maps != null && !maps.isEmpty() && maps.get(0) != null) {
                Map<String, Object> row = maps.get(0);
                Object a = row.get("customerReadMaxId");
                Object b = row.get("csReadMaxId");
                if (a instanceof Number) customerReadMaxId = ((Number) a).longValue();
                else if (a != null) { try { customerReadMaxId = Long.parseLong(String.valueOf(a)); } catch (Exception ignore) {} }
                if (b instanceof Number) csReadMaxId = ((Number) b).longValue();
                else if (b != null) { try { csReadMaxId = Long.parseLong(String.valueOf(b)); } catch (Exception ignore) {} }
            }
            // 否则保持默认 0，不做任何赋值
        }

        // 4) 本轮期间被撤回的历史消息 ID：只查 id 列（可选按 afterId 截断老消息）
        List<Long> revokedIds = java.util.Collections.emptyList();
        if (sinceRevokedAt != null && sinceRevokedAt > 0) {
            LocalDateTime since = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(sinceRevokedAt),
                    java.time.ZoneId.systemDefault()
            );

            QueryWrapper<Message> rq = new QueryWrapper<Message>()
                    .select("id")
                    .eq("conversation_id", conversationId)
                    .eq("is_revoked", 1)
                    .gt("revoked_at", since);
            if (afterId != null && afterId > 0) {
                rq.le("id", afterId);  // 只同步“旧消息”的撤回
            }

            List<Object> ids = messageService.getBaseMapper().selectObjs(rq);
            revokedIds = ids.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(o -> ((Number) o).longValue())
                    .toList();
        }

        // 5) 响应
        Map<String, Object> payload = new LinkedHashMap<>();
        for (Message m : recs) {
            if (m.getCreatedAt() != null) {
                m.setCreatedAtTs(m.getCreatedAt()
                        .atZone(ZoneOffset.UTC)   // 统一按 UTC 转换
                        .toInstant()
                        .toEpochMilli());
            }
        }
        payload.put("records", recs);
        payload.put("total", result.getTotal());
        payload.put("size", result.getSize());
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("customerReadMaxId", customerReadMaxId);
        payload.put("csReadMaxId", csReadMaxId);
        payload.put("serverTime", System.currentTimeMillis()); // 作为前端下次 sinceRevokedAt 的游标
        payload.put("revokedIds", revokedIds);

        return ApiResp.ok(payload);
    }

    // ================== 发送消息（自动识别图片/视频） ==================
    @PostMapping
    public ApiResp<Message> send(@RequestBody @Valid SendMessageReq req) {
        // ★ 强校验
        Conversation conv = mustLoadConv(req.getConversationId());
        if (conv == null) {
            return ApiResp.fail("conversation not found: call /customers/ensure first");
        }

        // 自动识别 contentType（未传或传0时）
        int ct = (req.getContentType() == null || req.getContentType() == 0)
                ? smartDetectCt(req.getContent())
                : req.getContentType();

        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        Message m = new Message();
        m.setConversationId(req.getConversationId());
        m.setAgentId(conv.getAgentId());
        m.setSenderType(req.getSenderType());
        m.setSenderUserId(ctx != null ? ctx.getUserId() : null);
        m.setContentType(ct);
        m.setContent(req.getContent());
        m.setMetaJson(req.getMetaJson());
        m.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        m.setCreatedAtTs(System.currentTimeMillis());
        m.setIsRevoked(0);
        // 默认未读：0；前端“已发送/已读”由轮询 /list 的 waterline 决定
        messageService.save(m);

        conv.setLastMessageAt(m.getCreatedAt());
        conversationService.updateById(conv);
        return ApiResp.ok(m);
    }

    // ================== 上传接口（保存到 /www/wwwroot/file/{conversationId}/） ==================
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<List<FileInfo>> upload(@RequestParam Long conversationId,
                                          @RequestPart("files") MultipartFile[] files) {
        // ★ 强校验
        Conversation conv = mustLoadConv(conversationId);
        if (conv == null) {
            return ApiResp.fail("conversation not found: call /customers/ensure first");
        }
        if (files == null || files.length == 0) {
            return ApiResp.fail("no files");
        }
        try {
            Path convDir = Path.of(ROOT_DIR, String.valueOf(conversationId));
            if (Files.notExists(convDir)) Files.createDirectories(convDir);

            List<FileInfo> list = new ArrayList<>();
            for (MultipartFile f : files) {
                if (f.isEmpty()) continue;

                // 生成文件名
                String original = Optional.ofNullable(f.getOriginalFilename()).orElse("unknown");
                String ext = extOf(original);
                String uuid = UUID.randomUUID().toString().replace("-", "");
                String newName = uuid + (ext.isEmpty() ? "" : "." + ext);

                Path dest = convDir.resolve(newName);
                try (InputStream in = f.getInputStream()) {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                }

                // 先取客户端传来的 MIME，再退回到系统探测；去掉 ";codecs=opus" 等参数
                String clientMime = normalizeMime(f.getContentType());
                String probedMime = normalizeMime(safeProbeContentType(dest));
                String mime = firstNonEmptyNonOctet(clientMime, probedMime);
                if (mime == null) mime = "application/octet-stream";

                String url = URL_PREFIX + "/" + conversationId + "/" + newName;

                // 更稳的类型推断：优先用 MIME 的主类，其次用扩展名
                int ct = detectByExtOrMime(ext, mime);

                list.add(new FileInfo(url, original, Files.size(dest), mime, ct));

            }
            return ApiResp.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResp.fail("upload error");
        }
    }

    // ================== 撤回 ==================
    @PostMapping("/revoke")
    public ApiResp<Boolean> revoke(@RequestBody @Valid RevokeMessageReq req) {
        Message m = messageService.getById(req.getMessageId());
        if (m == null) return ApiResp.fail("message not found");
        m.setIsRevoked(1);
        m.setRevokedAt(LocalDateTime.now());
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx != null) m.setRevokedByUserId(ctx.getUserId());
        m.setRevokeReason(req.getReason());
        messageService.updateById(m);
        return ApiResp.ok(true);
    }

    // ================== 工具方法 ==================

    /** 未传 contentType 时，依据 URL/扩展名猜测（图片/视频/音频；文本走默认） */
    private static int smartDetectCt(String content) {
        if (!StringUtils.hasText(content)) return CT_TEXT;
        String lower = content.toLowerCase(Locale.ROOT);

        // data:URL
        if (lower.startsWith("data:image/")) return CT_IMAGE;
        if (lower.startsWith("data:video/")) return CT_VIDEO;
        if (lower.startsWith("data:audio/")) return CT_AUDIO;

        // 扩展名（容忍 query 中带 filename=xxx.mp3 的情况）
        String[] IMG = {"jpg","jpeg","png","gif","webp","bmp","svg","avif"};
        String[] VID = {"mp4","mov","avi","mkv","webm","m4v","wmv","3gp"};
        String[] AUD = {"m4a","mp3","wav","ogg","oga","aac","flac","weba","opus","webm"};

        for (String e : IMG) if (containsExt(lower, e)) return CT_IMAGE;
        for (String e : VID) if (containsExt(lower, e)) return CT_VIDEO;
        for (String e : AUD) if (containsExt(lower, e)) return CT_AUDIO;

        return CT_TEXT;
    }
    private static boolean containsExt(String text, String ext) {
        return text.contains("." + ext) || text.contains("filename=") && text.contains("." + ext);
    }


    // 更稳的类型推断：先看 MIME 主类，再看扩展名
    private static int detectByExtOrMime(String ext, String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT).trim();
        String e = ext  == null ? "" : ext.toLowerCase(Locale.ROOT).trim();

        // 1) 先用 MIME 主类（去掉参数后）
        if (m.startsWith("image/")) return CT_IMAGE;
        if (m.startsWith("video/")) return CT_VIDEO;
        if (m.startsWith("audio/")) return CT_AUDIO;

        // 2) 再用扩展名兜底（考虑音频/视频常见后缀）
        if (in(e, "png","jpg","jpeg","gif","webp","bmp","svg","avif")) return CT_IMAGE;
        if (in(e, "mp4","webm","mov","m4v","avi","mkv","wmv","3gp"))   return CT_VIDEO;
        if (in(e, "m4a","mp3","wav","ogg","oga","aac","flac","weba","opus")) return CT_AUDIO;

        // 3) 仍然不确定：按文件处理
        return CT_FILE;
    }

    private static boolean in(String v, String... arr) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }

    // 只取 MIME 的主类型，去掉 ";xxx" 参数并统一小写
    private static String normalizeMime(String s) {
        if (s == null || s.isBlank()) return null;
        int i = s.indexOf(';');
        String base = (i >= 0 ? s.substring(0, i) : s).trim().toLowerCase(Locale.ROOT);
        return base.isEmpty() ? null : base;
    }

    // 某些系统 probe 可能抛异常或返回 null
    private static String safeProbeContentType(Path p) {
        try { return Files.probeContentType(p); } catch (Exception ignore) { return null; }
    }

    // 选择第一个非空且不是 application/octet-stream 的 MIME
    private static String firstNonEmptyNonOctet(String... mimes) {
        for (String m : mimes) {
            if (m != null && !m.isBlank() && !"application/octet-stream".equals(m)) return m;
        }
        return null;
    }

    // 可靠的扩展名获取（带 ? 查询串也能处理）
    private static String extOf(String name) {
        if (name == null) return "";
        int q = name.indexOf('?');
        if (q >= 0) name = name.substring(0, q);
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

}
