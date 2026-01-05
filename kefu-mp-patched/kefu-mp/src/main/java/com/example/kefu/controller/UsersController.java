package com.example.kefu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kefu.common.ApiResp;
import com.example.kefu.common.CodeGenerator;
import com.example.kefu.entity.User;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户管理（分页/搜索/创建/修改/删除/状态修改）
 * - 角色明文：ROOT / SUPER_AGENT / AGENT / CS
 * - 非 ROOT：仅能操作“自己为根的代理树”下的用户，并且禁止操作自己
 * - ROOT：无条件全量可见/可操作（列表也不再排除自己）
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UsersController {

    private final UserService userService;

    private static final Set<String> ROLES = Set.of("ROOT", "SUPER_AGENT", "AGENT", "CS");

    private boolean isRoot(AuthContext ctx) {
        return ctx != null && StringUtils.equalsIgnoreCase(ctx.getRole(), "ROOT");
    }

    /**
     * 递归构建“自己为根”的可见代理ID集合（包含自己）
     */
    List<Long> buildVisibleAgentIds(Long rootUserId) {
        Set<Long> scope = new LinkedHashSet<>();
        Set<Long> frontier = new LinkedHashSet<>();
        scope.add(rootUserId);
        frontier.add(rootUserId);

        while (!frontier.isEmpty()) {
            List<Long> parents = new ArrayList<>(frontier);
            frontier.clear();

            List<User> children = userService.lambdaQuery()
                    .in(User::getAgentId, parents)
                    .select(User::getId)
                    .list();

            for (User u : children) {
                Long id = u.getId();
                if (id != null && scope.add(id)) {
                    frontier.add(id);
                }
            }
        }
        return new ArrayList<>(scope);
    }

    // ========================= 列表（分页 + 搜索） =========================
    @GetMapping
    public ApiResp<Page<User>> list(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "12") long size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String role,
                                    @RequestParam(required = false) Integer status) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        LambdaQueryWrapper<User> q = new LambdaQueryWrapper<>();

        if (!isRoot(ctx)) {
            List<Long> visibleAgentIds = buildVisibleAgentIds(ctx.getUserId());
            q.in(!visibleAgentIds.isEmpty(), User::getAgentId, visibleAgentIds);
            q.ne(User::getId, ctx.getUserId()); // 非 ROOT：列表不显示自己
        }
        if (StringUtils.isNotBlank(keyword)) {
            q.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getDisplayName, keyword)
                    .or().like(User::getPhone, keyword)
                    .or().like(User::getCode, keyword));
        }
        if (StringUtils.isNotBlank(role)) q.eq(User::getRole, role); // 明文角色
        if (status != null) q.eq(User::getStatus, status);

        q.orderByDesc(User::getId);
        Page<User> p = userService.page(Page.of(page, size), q);
        p.getRecords().forEach(this::scrubSensitive);
        return ApiResp.ok(p);
    }

    // ========================= 详情 =========================
    @GetMapping("/{id}")
    public ApiResp<User> get(@PathVariable Long id) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        User u = userService.getById(id);
        if (u == null) return ApiResp.fail("user not found");

        if (!isRoot(ctx)) {
            List<Long> visibleAgentIds = buildVisibleAgentIds(ctx.getUserId());
            boolean isSelf = Objects.equals(u.getId(), ctx.getUserId());
            boolean inScope = (u.getAgentId() != null && visibleAgentIds.contains(u.getAgentId()));
            if (!isSelf && !inScope) return ApiResp.fail("FORBIDDEN");
        }

        scrubSensitive(u);
        return ApiResp.ok(u);
    }

    // ========================= 新增 =========================
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResp<User> create(@RequestBody @Valid CreateUserReq req) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        if (!ROLES.contains(req.getRole())) return ApiResp.fail("invalid role");
        if (!isRoot(ctx) && "ROOT".equalsIgnoreCase(req.getRole())) return ApiResp.fail("FORBIDDEN_CREATE_ROOT");

        if (req.getAgentId() == null) return ApiResp.fail("agentId required");

        // 目标代理可见性校验（非 ROOT 只能在授权范围内创建）
        if (!isRoot(ctx)) {
            List<Long> visibleAgentIds = buildVisibleAgentIds(ctx.getUserId());
            if (!visibleAgentIds.contains(req.getAgentId())) return ApiResp.fail("FORBIDDEN_TARGET_AGENT");
        }

        // 用户名唯一
        long cnt = userService.lambdaQuery()
                .eq(User::getUsername, req.getUsername())
                .count();
        if (cnt > 0) return ApiResp.fail("用户名已存在");

        // ============= 核心逻辑：席位限制校验（按代理） =============
        User agent = userService.getById(req.getAgentId());
        if (agent == null) return ApiResp.fail("代理不存在");

        // 仅当创建客服(CS)时，才占用/校验坐席；如需所有角色都受限，去掉 isCS 判断
        boolean isCS = "CS".equalsIgnoreCase(req.getRole());
        if (isCS) {
            Integer seatCount = agent.getSeatCount(); // 需要在 User 实体中有 seatCount 字段
            if (seatCount == null) {
                return ApiResp.fail("该代理未配置坐席数（seatCount 不能为空），请先配置后再创建");
            }
            if (seatCount <= 0) {
                return ApiResp.fail("您的可用坐席数量不足，无法创建更多（当前坐席数量 = 0），请购买/升级套餐后再创建");
            }

            // 统计该代理当前已用坐席（仅统计启用中的 CS）
            long used = userService.lambdaQuery()
                    .eq(User::getAgentId, req.getAgentId())
                    .eq(User::getRole, "CS")
                    .eq(User::getStatus, 1)
                    .count();

            // 预占 1 席；若已满则阻止创建
            if (used >= seatCount) {
                return ApiResp.fail("坐席已用完（" + used + "/" + seatCount + "），请升级套餐或释放坐席");
            }
        }
        // =====================================================

        User u = new User();
        u.setAgentId(req.getAgentId());
        u.setRole(req.getRole());
        u.setUsername(req.getUsername());
        u.setPasswordHash(req.getPassword()); // TODO: 确认是否已加密
        u.setDisplayName(req.getDisplayName());
        u.setEmail(req.getEmail());
        u.setPhone(req.getPhone());
        u.setCode(CodeGenerator.generateCode(12));
        u.setStatus(req.getStatus() == null ? 1 : req.getStatus());
        u.setTotpEnabled(req.getTotpEnabled() != null ? req.getTotpEnabled() : 0);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());

        userService.save(u);
        scrubSensitive(u);
        return ApiResp.ok(u);
    }

    // ========================= 修改 =========================
    @PutMapping("/{id}")
    public ApiResp<User> update(@PathVariable Long id, @RequestBody @Valid UpdateUserReq req) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        User u = userService.getById(id);
        if (u == null) return ApiResp.fail("user not found");

        // —— 权限：非 ROOT 允许修改“自己”，但不允许越权修改不在自己树下的其他账号
        if (!isRoot(ctx)) {
            List<Long> visibleAgentIds = buildVisibleAgentIds(ctx.getUserId());
            boolean isSelf = Objects.equals(u.getId(), ctx.getUserId());
            boolean inScope = (u.getAgentId() != null && visibleAgentIds.contains(u.getAgentId()));
            if (!isSelf && !inScope) {
                return ApiResp.fail("FORBIDDEN");
            }
        }

        // 账号类字段（保持你原有逻辑）
        if (StringUtils.isNotBlank(req.getUsername()) && !req.getUsername().equals(u.getUsername())) {
            long cnt = userService.lambdaQuery()
                    .eq(User::getUsername, req.getUsername())
                    .ne(User::getId, id).count();
            if (cnt > 0) return ApiResp.fail("用户名已存在");
            u.setUsername(req.getUsername());
        }
        if (req.getAgentId() != null && isRoot(ctx)) u.setAgentId(req.getAgentId());
        if (StringUtils.isNotBlank(req.getRole())) {
            if (!ROLES.contains(req.getRole())) return ApiResp.fail("invalid role");
            if (!isRoot(ctx) && "ROOT".equalsIgnoreCase(req.getRole())) return ApiResp.fail("FORBIDDEN_SET_ROOT");
            u.setRole(req.getRole());
        }
        if (StringUtils.isNotBlank(req.getPassword())) u.setPasswordHash(req.getPassword());
        if (req.getDisplayName() != null) u.setDisplayName(req.getDisplayName());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getPhone() != null) u.setPhone(req.getPhone());
        if (req.getCode() != null) u.setCode(req.getCode());
        if (req.getStatus() != null) u.setStatus(req.getStatus());
        if (req.getTotpEnabled() != null) u.setTotpEnabled(req.getTotpEnabled());
        if (req.getAutoReply() != null) u.setAutoReply(req.getAutoReply());

        // ★★★ 平台信息（关键补充）
        if (req.getBrandName() != null) {
            u.setBrandName(req.getBrandName());
        }
        if (req.getBrandLogo() != null) u.setBrandLogo(req.getBrandLogo());
        if (req.getBrandIntro() != null) u.setBrandIntro(req.getBrandIntro());
        if (req.getCsName() != null) u.setCsName(req.getCsName());
        if (req.getCsLogo() != null) u.setCsLogo(req.getCsLogo());
        if (req.getDomain() != null) u.setDomain(req.getDomain());

        u.setUpdatedAt(LocalDateTime.now());
        userService.updateById(u);
        scrubSensitive(u);
        return ApiResp.ok(u);
    }


    // ========================= 状态修改 =========================
    @PutMapping("/{id}/status")
    public ApiResp<Boolean> setStatus(@PathVariable Long id, @RequestBody StatusReq req) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        User u = userService.getById(id);
        if (u == null) return ApiResp.fail("user not found");

        if (!isRoot(ctx)) {
            List<Long> visibleAgentIds = buildVisibleAgentIds(ctx.getUserId());
            if (Objects.equals(u.getId(), ctx.getUserId())
                    || u.getAgentId() == null || !visibleAgentIds.contains(u.getAgentId())) {
                return ApiResp.fail("FORBIDDEN");
            }
        }

        u.setStatus(req.getStatus());
        u.setUpdatedAt(LocalDateTime.now());
        boolean ok = userService.updateById(u);
        return ok ? ApiResp.ok(true) : ApiResp.fail("update failed");
    }

    // ========================= 删除/批量删除 =========================
    @DeleteMapping("/{id}")
    public ApiResp<Boolean> delete(@PathVariable Long id) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        if (!isRoot(ctx) && Objects.equals(ctx.getUserId(), id)) return ApiResp.fail("cannot delete self");

        User u = userService.getById(id);
        if (u == null) return ApiResp.fail("user not found");

        if (!isRoot(ctx)) {
            List<Long> visibleAgentIds = buildVisibleAgentIds(ctx.getUserId());
            if (u.getAgentId() == null || !visibleAgentIds.contains(u.getAgentId())) return ApiResp.fail("FORBIDDEN");
        }

        boolean ok = userService.removeById(id);
        return ok ? ApiResp.ok(true) : ApiResp.fail("delete failed");
    }

    @DeleteMapping
    public ApiResp<Boolean> batchDelete(@RequestParam String ids) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        List<Long> list = Arrays.stream(StringUtils.defaultString(ids).split(","))
                .filter(StringUtils::isNotBlank).map(Long::valueOf).collect(Collectors.toList());
        if (list.isEmpty()) return ApiResp.fail("ids required");

        List<User> targets = userService.listByIds(list);

        if (!isRoot(ctx)) {
            List<Long> visibleAgentIds = buildVisibleAgentIds(ctx.getUserId());
            for (User u : targets) {
                if (Objects.equals(u.getId(), ctx.getUserId())) return ApiResp.fail("cannot delete self");
                if (u.getAgentId() == null || !visibleAgentIds.contains(u.getAgentId()))
                    return ApiResp.fail("FORBIDDEN");
            }
        }
        boolean ok = userService.removeBatchByIds(list);
        return ok ? ApiResp.ok(true) : ApiResp.fail("delete failed");
    }

    // ========================= DTO =========================
    @Data
    public static class CreateUserReq {
        @NotNull
        private Long agentId;                 // 必填
        @NotBlank
        private String role;                 // ROOT/SUPER_AGENT/AGENT/CS
        @NotBlank
        @Size(min = 2, max = 32)
        private String username;
        @NotBlank
        @Size(min = 3, max = 64)
        private String password; // 明文
        private String displayName;
        private String email;
        private String phone;
        private String code;
        private Integer status;       // 1启用/0禁用
        private Integer totpEnabled;  // 0/1
    }

    @Data
    public static class UpdateUserReq {
        private Long agentId;
        private String role;            // 明文
        private String username;
        private String password;
        private String brandName;   // ✅ 平台名字
        private String brandLogo;   // ✅ 平台 Logo（URL）
        private String brandIntro;  // ✅ 平台介绍
        private String domain;  // ✅ 平台介绍
        private String csName;
        private String csLogo;
        private String autoReply;
        private String displayName;
        private String email;
        private String phone;
        private String code;
        private Integer status;
        private Integer totpEnabled;
    }

    @Data
    public static class StatusReq {
        @NotNull
        private Integer status; // 1/0
    }

    private void scrubSensitive(User u) {
        if (u == null) return;
        u.setPasswordHash(null);
        u.setTotpSecret(null);
        u.setTotpBackupCodes(null);
    }
}
