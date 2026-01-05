// src/main/java/com/example/kefu/dto/ConversationListItem.java
package com.example.kefu.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationListItem {
    // 原 Conversation 字段
    private Long id;
    private Long agentId;
    private Long customerId;
    private Long csUserId;
    private String channel;
    private String region;
    private Integer status;
    private LocalDateTime startedAt;
    private LocalDateTime closedAt;
    private LocalDateTime lastMessageAt;
    private Long csLastReadMessageId;
    private LocalDateTime csLastReadAt;
    private Long customerLastReadMessageId;
    private LocalDateTime customerLastReadAt;

    // 新增：最后一条消息精简信息
    private Long lastMessageId;
    private Integer lastMessageContentType; // 1/2/3/4
    private String lastMessageContent;      // 文本或URL
    private Integer lastMessageIsRevoked;   // 0/1
    private Integer lastMessageSenderType;  // 1=客户 2=后台
    private String lastMessageFileName;     // 可选
    private Long lastMessageFileSize;       // 可选
    private LocalDateTime lastMessageCreatedAt;

    private Integer unreadCount;
    private String customerNote;
}
