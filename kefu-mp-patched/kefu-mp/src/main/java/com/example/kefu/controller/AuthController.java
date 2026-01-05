package com.example.kefu.controller;

import com.example.kefu.common.ApiResp;
import com.example.kefu.common.JwtUtil;
import com.example.kefu.dto.LoginReq;
import com.example.kefu.entity.User;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final OpLogController opLogController;
    private final JwtUtil jwt;

    /** 写入 httpOnly 的 refresh_token */
    private void setRefreshCookie(HttpServletRequest req, HttpServletResponse resp, String refreshToken) {
        boolean secure = req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto"));

        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(secure)
                // 若前后端不同顶级域，改成 None；同域保留 Lax 亦可
                .sameSite("None")
                .path("/")
                .maxAge(Math.max(1, jwt.getRefreshExpSeconds()))
                .build();
        resp.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletRequest req, HttpServletResponse resp) {
        boolean secure = req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto"));
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("None")
                .path("/")
                .maxAge(0)
                .build();
        resp.addHeader("Set-Cookie", cookie.toString());
    }

    @PostMapping("/login")
    public ApiResp<Map<String, Object>> login(@RequestBody @Valid LoginReq req,
                                              HttpServletRequest http,
                                              HttpServletResponse resp) {
        User u = userService.lambdaQuery()
                .eq(User::getUsername, req.getUsername())
                .last("limit 1")
                .one();

        boolean pwdOk = (u != null && StringUtils.equals(u.getPasswordHash(), req.getPassword()));
        // 账号可用：status==1 且 未删除
        boolean enabled = (u != null) &&
                (u.getStatus() == null || u.getStatus() == 1) &&
                (u.getDeletedAt() == null);

        String ip = Optional.ofNullable(http.getRemoteAddr()).orElse("未知IP");
        // 统一记录审计，但 success 要根据最终结果


        if (!pwdOk) {
            opLogController.addLog(ip, u != null ? u.getId() : null, "登录", "登录失败：用户名或密码错误！");
            return ApiResp.fail("用户名或密码错误");
        }
        if (!enabled) {
            opLogController.addLog(ip, u.getId(), "登录", "登录失败：当前账户不可用！");
            return ApiResp.fail("当前账户不可用");
        }

        // 到这里才算真正成功
        opLogController.addLog(ip, u.getId(), "登录", "登录成功！");

        // 根据新的 JwtUtil 签名：uid + uname + role + agentId
        String accessToken  = jwt.createAccessToken(u.getId(), u.getUsername(), u.getRole(), u.getAgentId());
        String refreshToken = jwt.createRefreshToken(u.getId());

        // ★ 把当前 accessToken 写到 user.token（覆盖旧值）
        userService.lambdaUpdate()
                .eq(User::getId, u.getId())
                .set(User::getToken, accessToken)   // 确保 User 实体有 token 字段的 getter/setter
                .set(User::getLoginIp, ip)
                .set(User::getLoginTime, LocalDateTime.now())
                .update();

        // 写入 httpOnly 刷新 cookie
        setRefreshCookie(http, resp, refreshToken);

        Map<String, Object> out = new HashMap<>();
        out.put("userId", u.getId());
        out.put("username", u.getUsername());
        out.put("role", u.getRole());
        out.put("agentId", u.getAgentId());
        out.put("token", accessToken);
        out.put("needTotp", (u.getTotpEnabled() != null && u.getTotpEnabled() == 1));
        return ApiResp.ok(out);
    }

    /** 用 httpOnly 的 refresh_token 刷新 accessToken（同时校验账号仍然可用） */
    @PostMapping("/refresh")
    public ApiResp<Map<String, String>> refresh(HttpServletRequest req, HttpServletResponse resp) {
        String rt = null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("refresh_token".equals(c.getName())) {
                    rt = c.getValue();
                    break;
                }
            }
        }
        if (rt == null || rt.isEmpty()) return ApiResp.fail("NO_REFRESH_TOKEN");

        try {
            var jws = jwt.parse(rt);
            var claims = jws.getBody();
            if (!"refresh".equals(claims.get("typ"))) return ApiResp.fail("BAD_TOKEN_TYPE");

            Long userId = Long.valueOf(claims.getSubject());
            User u = userService.getById(userId);
            if (u == null) return ApiResp.fail("USER_NOT_FOUND");

            boolean enabled = (u.getStatus() == null || u.getStatus() == 1) && (u.getDeletedAt() == null);
            if (!enabled) return ApiResp.fail("ACCOUNT_DISABLED");

            String newAccess = jwt.createAccessToken(u.getId(), u.getUsername(), u.getRole(), u.getAgentId());

            // （可选）旋转 refresh；不旋转也至少要“重写同值”以续期 Cookie 过期
            String newRefresh = jwt.createRefreshToken(u.getId());
            setRefreshCookie(req, resp, newRefresh); // ✅ 续期 refresh cookie

            userService.lambdaUpdate()
                    .eq(User::getId, u.getId())
                    .set(User::getToken, newAccess)
                    .update();

            return ApiResp.ok(Map.of("token", newAccess));
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return ApiResp.fail("REFRESH_EXPIRED");
        } catch (Exception e) {
            return ApiResp.fail("INVALID_REFRESH_TOKEN");
        }
    }

    @PostMapping("/logout")
    public ApiResp<Boolean> logout(
            @RequestHeader(value = "Authorization", required = false) String authz,
            HttpServletRequest req,
            HttpServletResponse resp) {

        String token = (authz != null && authz.startsWith("Bearer ")) ? authz.substring(7) : null;
        if (StringUtils.isNotBlank(token)) {
            try {
                var jws = jwt.parse(token);
                var claims = jws.getBody();
                Long userId = Long.valueOf(String.valueOf(claims.get("uid"))); // 依据你的 JwtUtil 里 uid 的存法
                if (userId != null) {
                    userService.lambdaUpdate()
                            .eq(User::getId, userId)
                            .set(User::getToken, null) // ★ 清空库里的 token
                            .update();
                }
                opLogController.addLog(Optional.ofNullable(req.getRemoteAddr()).orElse("未知IP"), userId, "退出登录", "成功！");
            } catch (Exception ignore) {
                // token 无效也忽略，保持幂等
            }
        }
        clearRefreshCookie(req, resp);
        return ApiResp.ok(true);
    }

    @GetMapping("/me")
    public ApiResp<Object> me() {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        return ApiResp.ok(ctx);
    }

}
