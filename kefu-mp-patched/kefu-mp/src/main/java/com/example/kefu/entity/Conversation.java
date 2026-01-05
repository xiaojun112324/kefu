package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {
    @TableId
    private Long id;
    private Long agentId;
    private Long customerId;
    private Long csUserId;
    private String channel;
    private Integer status;
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;
    private LocalDateTime lastMessageAt;

    private Long csLastReadMessageId;
    private LocalDateTime csLastReadAt;
    private Long customerLastReadMessageId;
    private LocalDateTime customerLastReadAt;
}
