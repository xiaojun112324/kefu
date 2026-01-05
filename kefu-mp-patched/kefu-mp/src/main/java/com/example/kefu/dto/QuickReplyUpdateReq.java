// src/main/java/com/xx/app/qa/dto/QuickReplyUpdateReq.java
package com.example.kefu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuickReplyUpdateReq {
    @NotBlank(message = "消息内容不能为空")
    private String msg;
    private Integer sort = 0;
}
