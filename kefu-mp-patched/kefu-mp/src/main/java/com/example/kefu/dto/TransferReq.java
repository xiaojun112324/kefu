package com.example.kefu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TransferReq {
    @NotNull
    private Long customerId;
    private Long fromCsId;
    @NotNull
    private Long toCsId;
    private String reason;
}
