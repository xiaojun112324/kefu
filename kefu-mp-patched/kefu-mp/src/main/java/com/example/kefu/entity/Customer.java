package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {
    @TableId
    private Long id;
    private Long agentId;
    private String visitorToken;
    private String name;
    private String note;
    private String email;
    private String phone;
    private String firstIp;
    private String lastIp;
    private String region;
    private String userAgent;
    private Long assignedCsId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
