package com.example.kefu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kefu.common.ApiResp;
import com.example.kefu.entity.OpLog;
import com.example.kefu.service.OpLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/op-logs")
@RequiredArgsConstructor
public class OpLogController {

    private final OpLogService opLogService;

    /**
     * 分页查询操作日志：
     * - 按 userId 精确过滤（可选）
     * - 按 q 对 op / opResult 进行 LIKE 模糊查询（可选）
     */
    @GetMapping
    public ApiResp<Page<OpLog>> list(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "50") long size,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(required = false, name = "q") String keyword) {

        LambdaQueryWrapper<OpLog> qw = new LambdaQueryWrapper<OpLog>()
                .eq(userId != null, OpLog::getUserId, userId)
                // (op LIKE ? OR op_result LIKE ?)
                .and(StringUtils.isNotBlank(keyword),
                        w -> w.like(OpLog::getOp, keyword)
                                .or()
                                .like(OpLog::getOpResult, keyword))
                .orderByDesc(OpLog::getCreatedAt);

        Page<OpLog> pages = opLogService.page(Page.of(page, size), qw);
        return ApiResp.ok(pages);
    }
    public void addLog(String ip, Long userId, String op, String opResult) {
        OpLog opLog = new OpLog();
        opLog.setUserId(userId);
        opLog.setIp(ip);
        opLog.setOp(op);
        opLog.setOpResult(opResult);
        opLog.setCreatedAt(LocalDateTime.now());
        opLogService.save(opLog);
    }


}
