package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message")
public class Message {
    @TableId
    private Long id;
    private Long conversationId;
    private Long agentId;
    private Integer senderType;
    private Integer isRead;
    private Long senderUserId;
    private Integer contentType;
    private String content;
    private String metaJson;
    private String ip;
    private LocalDateTime createdAt;
    @TableField(exist = false)
    private Long createdAtTs;
    private LocalDateTime editedAt;
    private LocalDateTime deletedAt;
    private Integer isRevoked;
    private LocalDateTime revokedAt;
    private Long revokedByUserId;
    private String revokeReason;
}
