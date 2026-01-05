package com.example.kefu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assignment_history")
public class AssignmentHistory {
    @TableId
    private Long id;
    private Long agentId;
    private Long customerId;
    private Long fromCsId;
    private Long toCsId;
    private Long byUserId;
    private String reason;
    private LocalDateTime createdAt;
}
