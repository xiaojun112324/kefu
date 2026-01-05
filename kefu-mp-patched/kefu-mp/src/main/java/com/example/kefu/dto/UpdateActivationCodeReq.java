// src/main/java/com/example/kefu/dto/code/UpdateActivationCodeReq.java
package com.example.kefu.dto;

import lombok.Data;

@Data
public class UpdateActivationCodeReq {
    /** 允许修改：function / value / status / useUserId（code 一般不改） */
    private Integer function;     // 1/2
    private Integer value;        // >0
    private Integer status;       // 0/1
    private Long useUserId;       // 设置/清空使用人
}
