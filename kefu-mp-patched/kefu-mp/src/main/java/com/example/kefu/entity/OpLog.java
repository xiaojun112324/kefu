package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("op_log")
public class OpLog {
    @TableId
    private Long id;
    private Long userId;
    private String op;
    private String opResult;
    private String ip;
    private LocalDateTime createdAt;
}
