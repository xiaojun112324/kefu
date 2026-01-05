package com.example.kefu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RevokeMessageReq {
    @NotNull
    private Long messageId;
    private String reason;
}
