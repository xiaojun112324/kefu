// src/main/java/com/example/kefu/dto/code/CreateActivationCodeReq.java
package com.example.kefu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateActivationCodeReq {
    /** 1 增加可用天数；2 增加座席数 */
    @NotNull
    private Integer function;

    /** 值（>0） */
    @NotNull @Min(1)
    private Integer value;
}
