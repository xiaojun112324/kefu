package com.example.kefu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginReq {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private String totpCode; // 可选：二次验证
}
