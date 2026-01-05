package com.example.kefu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MarkReadReq {
    @NotNull
    private Long conversationId;
    @NotNull
    private String side; // "CS" or "CUSTOMER"
}
