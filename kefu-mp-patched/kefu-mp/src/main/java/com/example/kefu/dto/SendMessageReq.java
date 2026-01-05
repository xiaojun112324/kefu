package com.example.kefu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageReq {
    @NotNull
    private Long conversationId;
    @NotNull
    private Integer contentType; // 1 text etc
    @NotNull
    private Integer senderType;  // 1 customer 2 cs 3 system
    private String content;
    private String metaJson;
}
