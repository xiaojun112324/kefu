package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId
    private Long id;
    private Long agentId;
    private String role;
    private String username;
    private String passwordHash;
    private String displayName;
    private String brandName;
    private String brandLogo;
    private String brandIntro;
    private String csName;
    private String csLogo;
    private String autoReply;
    private String email;
    private String phone;
    private Integer seatCount;
    private String code;
    private String domain;
    private Integer status;
    private Integer totpEnabled;
    private String totpSecret;
    private String totpBackupCodes;
    private String token;
    private String loginIp;
    private LocalDateTime loginTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime expireAt;
}
