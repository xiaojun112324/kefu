package com.example.kefu.common;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long accessExpMillis;
    private final long refreshExpMillis;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-exp-minutes:15}") long accessExpMinutes,
            @Value("${jwt.refresh-exp-days:7}") long refreshExpDays
    ) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 要求 >= 256 bits（32 字节）
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessExpMillis = Duration.ofMinutes(accessExpMinutes).toMillis();
        this.refreshExpMillis = Duration.ofDays(refreshExpDays).toMillis();
    }

    /** 只把非空字段放进 claims，避免 Map.of 的空值 NPE */
    public String createAccessToken(Long userId, String uname, String role, Long agentId) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + accessExpMillis))
                .claim("typ", "access")
                .claim("uid", userId)
                .claim("uname", uname);

        if (role != null && !role.isEmpty()) {
            builder.claim("role", role);
        }
        if (agentId != null) {
            builder.claim("agentId", agentId);
        }

        return builder.signWith(key, SignatureAlgorithm.HS256).compact();
    }

    public String createRefreshToken(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + refreshExpMillis))
                .claim("typ", "refresh")
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parse(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
    }

    public long getAccessExpSeconds()  { return accessExpMillis  / 1000; }
    public long getRefreshExpSeconds() { return refreshExpMillis / 1000; }
}