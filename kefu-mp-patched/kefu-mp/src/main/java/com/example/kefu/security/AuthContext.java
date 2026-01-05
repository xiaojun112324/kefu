package com.example.kefu.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class AuthContext {
    private Long userId;
    private String role;     // ROOT / SUPER_AGENT / AGENT / CS
    private String username;
    private String token;
    private Long agentId;    // null for ROOT
}
