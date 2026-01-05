// src/main/java/com/xx/app/qa/entity/QuickReply.java
package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("quick_replies")
public class QuickReply {
    @TableId
    private Long id;

    private Long userId;   // user_id
    private String msg;    // 文本
    private Integer sort;  // 排序
}
