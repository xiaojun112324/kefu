// src/main/java/com/example/kefu/security/HeaderAuthInterceptor.java
package com.example.kefu.security;

import com.example.kefu.common.JwtUtil;
import com.example.kefu.entity.User;
import com.example.kefu.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class HeaderAuthInterceptor implements HandlerInterceptor {

    public static final ThreadLocal<AuthContext> CTX = new ThreadLocal<>();

    private final JwtUtil jwt;
    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 没带 Authorization 头就放过（由各 Controller 再决定是否需要登录）
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            CTX.remove();
            return true;
        }

        String token = auth.substring(7);
        Jws<Claims> jws;
        try {
            jws = jwt.parse(token);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            CTX.remove();
            response.setHeader("X-Token-Status", "TOKEN_EXPIRED");
            write401(response, "TOKEN_EXPIRED");
            return false;
        } catch (JwtException e) {
            CTX.remove();
            response.setHeader("X-Token-Status", "INVALID_TOKEN");
            write401(response, "INVALID_TOKEN");
            return false;
        }

        Claims c = jws.getBody();
        Object typ = c.get("typ");
        if (typ == null || !"access".equals(typ.toString())) {
            CTX.remove();
            write401(response, "BAD_TOKEN_TYPE");
            return false;
        }

        Long uid = Long.valueOf(c.getSubject());
        String uname = (String) c.get("uname");
        String role = (String) c.get("role");
        Long agentId = null;
        Object aid = c.get("agentId");
        if (aid != null) agentId = Long.valueOf(String.valueOf(aid));

        // 写入上下文
        AuthContext ctx = new AuthContext();
        ctx.setUserId(uid);
        ctx.setUsername(uname);
        ctx.setRole(role);
        ctx.setAgentId(agentId);
        CTX.set(ctx);

        // 统一账号状态校验
        User u = userService.getById(uid);
        boolean enabled = (u != null) &&
                (u.getStatus() == null || u.getStatus() == 1) &&
                (u.getDeletedAt() == null);

        if (!enabled) {
            CTX.remove();
            clearRefreshCookie(request, response);
            write401(response, "ACCOUNT_DISABLED");
            return false;
        }

        // ★ 单点登录关键判断：请求头 token 必须等于数据库里的
        String dbToken = u.getToken();

        if (dbToken == null || !dbToken.equals(token)) {
            CTX.remove();
            clearRefreshCookie(request, response);
            write401(response, "ACCOUNT_CONFLICT");
            return false;
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CTX.remove();
    }

    private void write401(HttpServletResponse resp, String msg) throws IOException {
        resp.setStatus(401);
        resp.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        // 与 ApiResp.fail 对齐的最简 JSON
        resp.getWriter().write("{\"code\":-1,\"msg\":\"" + msg + "\"}");
    }

    private void clearRefreshCookie(HttpServletRequest req, HttpServletResponse resp) {
        boolean secure = req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto"));
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        resp.addHeader("Set-Cookie", cookie.toString());
    }
}
