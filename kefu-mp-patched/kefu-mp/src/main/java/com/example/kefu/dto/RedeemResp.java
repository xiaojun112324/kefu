// src/main/java/com/example/kefu/dto/code/RedeemResp.java
package com.example.kefu.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedeemResp {
    private Long codeId;
    private String code;
    private Integer function; // 1=天数 2=座席
    private Integer value;

    private Long userId;
    private LocalDateTime newExpireAt; // 兑换后用户的新到期时间
    private Integer newSeatCount;      // 兑换后用户的新座席数
}
