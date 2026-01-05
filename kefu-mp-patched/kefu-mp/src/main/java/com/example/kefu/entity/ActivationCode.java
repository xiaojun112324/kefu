// src/main/java/com/example/kefu/entity/ActivationCode.java
package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("activation_code") // ← 改成你的真实表名
public class ActivationCode {
    @TableId
    private Long id;

    /** 1 增加可用天数；2 增加座席数 */
    private Integer function;

    /** 激活码 */
    private String code;

    /** 可变参数：天数 / 座席数量 */
    private Integer value;

    /** 使用人 UID（未使用可为空） */
    @TableField("use_user_id")
    private Long useUserId;

    /** 创建人 UID */
    @TableField("created_user_id")
    private Long createdUserId;

    /** 1 未使用；0 已使用 */
    private Integer status;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;


    private String note;
}
