// src/main/java/com/example/kefu/controller/ActivationCodeController.java
package com.example.kefu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kefu.common.ApiResp;
import com.example.kefu.common.CodeGenerator;
import com.example.kefu.dto.CreateActivationCodeReq;
import com.example.kefu.dto.RedeemResp;
import com.example.kefu.entity.ActivationCode;
import com.example.kefu.entity.User;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.ActivationCodeService;
import com.example.kefu.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/activation-codes")
@RequiredArgsConstructor
public class ActivationCodeController {

    private final ActivationCodeService activationCodeService;
    private final UserService userService;

    /** 分页查询：支持 code / status / useUserId / function 过滤
     *  排序：未使用(status=1) 优先，其次按 created_at DESC，再按 id DESC */
    @GetMapping
    public ApiResp<Page<ActivationCode>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long useUserId,
            @RequestParam(required = false) Integer function
    ) {
        LambdaQueryWrapper<ActivationCode> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.isNotBlank(code), ActivationCode::getCode, code)
                .eq(status != null, ActivationCode::getStatus, status)
                .eq(useUserId != null, ActivationCode::getUseUserId, useUserId)
                .eq(function != null, ActivationCode::getFunction, function)
                // 关键：status=1 的在前，然后同组内按 id desc
                .last("ORDER BY (CASE WHEN status = 1 THEN 1 ELSE 0 END) DESC, id DESC");

        Page<ActivationCode> p = activationCodeService.page(Page.of(page, size), qw);
        return ApiResp.ok(p);
    }


    /** 新增：自动生成唯一 code；status 默认 1=未使用；created_user_id=当前UID */
    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    public ApiResp<ActivationCode> create(@RequestBody @Valid CreateActivationCodeReq req) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        // 校验 function
        if (req.getFunction() == null || (req.getFunction() != 1 && req.getFunction() != 2)) {
            return ApiResp.fail("invalid function (1=days, 2=seats)");
        }
        if (req.getValue() == null || req.getValue() <= 0) {
            return ApiResp.fail("value must be > 0");
        }

        ActivationCode ac = new ActivationCode();
        ac.setFunction(req.getFunction());
        ac.setValue(req.getValue());
        ac.setStatus(1); // 未使用
        ac.setUseUserId(null);
        ac.setCreatedUserId(ctx.getUserId());
        ac.setCreatedAt(LocalDateTime.now());

        // 生成唯一 code（重试避免碰撞）
        String code;
        int tries = 0;
        do {
            code = CodeGenerator.generateCode(16); // 你也可以改成 12/20 等
            tries++;
            if (tries > 5) return ApiResp.fail("generate code failed");
        } while (activationCodeService.count(new LambdaQueryWrapper<ActivationCode>()
                .eq(ActivationCode::getCode, code)) > 0);

        ac.setCode(code);

        activationCodeService.save(ac);
        return ApiResp.ok(ac);
    }

    /** 销毁：并发安全，仅允许对“未使用”激活码进行人工销毁；默认写入备注 */
    @PostMapping("/{id}/destroy")
    @Transactional(rollbackFor = Exception.class)
    public ApiResp<ActivationCode> destroy(@PathVariable Long id,
                                           @RequestParam(required = false) String note) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        String fallback = "人工销毁 UID=" + ctx.getUserId() +
                " @ " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String theNote = StringUtils.isNotBlank(note) ? note : fallback;

        // 原子作废：仅当 status=1（未使用）才能销毁
        int rows = activationCodeService.getBaseMapper().update(null,
                new UpdateWrapper<ActivationCode>()
                        .eq("id", id)
                        .eq("status", 1)
                        .set("status", 0)                 // 作废后归并到“已使用/已销毁”类
                        .set("use_user_id", ctx.getUserId()) // 记录操作者
                        .set("note", theNote)             // 默认备注
        );
        if (rows == 0) return ApiResp.fail("激活码不存在或已使用/已销毁");

        ActivationCode ac = activationCodeService.getById(id);
        return ApiResp.ok(ac);
    }

    /**
     * 兑换激活码：
     * - function=1：增加当前用户 expireAt 天数（时间类型）
     * - function=2：增加当前用户 seatCount
     * 并发安全：用 status 条件更新原子“占用”激活码，再原子更新用户字段
     */
    @PostMapping("/redeem")
    @Transactional(rollbackFor = Exception.class)
    public ApiResp<RedeemResp> redeem(@RequestParam String code
                                      //, @RequestParam(required = false) Long targetUserId // 可选：允许特权用户替他人兑换
    ) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");
        final Long uid = ctx.getUserId();
        // 如果允许指定：Long uid = (targetUserId != null ? targetUserId : ctx.getUserId());
        // 并检查权限：只有 ROOT/SUPER_AGENT 可代别人 uid 兑换

        // 1) 原子占用激活码（status: 1->0）
        int rows = activationCodeService.getBaseMapper().update(null,
                new UpdateWrapper<ActivationCode>()
                        .eq("code", code)
                        .eq("status", 1)           // 仅未使用
                        .set("status", 0)          // 标记已使用
                        .set("use_user_id", uid)   // 记录使用人
        );
        if (rows == 0) {
            return ApiResp.fail("激活码不存在或已被使用");
        }

        // 2) 读取激活码信息（用于判断 function/value）
        ActivationCode ac = activationCodeService.getOne(
                new LambdaQueryWrapper<ActivationCode>().eq(ActivationCode::getCode, code)
        );
        if (ac == null) {
            // 理论上不可能：上一步已匹配了 code
            return ApiResp.fail("兑换异常，请重试");
        }

        // 3) 原子更新用户字段（避免并发覆盖）
        //    MySQL 写法（IF / DATE_ADD / NOW）
        if (ac.getFunction() != null && ac.getFunction() == 1) {
            int days = Math.max(1, ac.getValue() == null ? 0 : ac.getValue());
            boolean ok = userService.update(
                    new UpdateWrapper<User>()
                            .eq("id", uid)
                            .setSql(
                                    "expire_at = IF(expire_at IS NULL OR expire_at < NOW(), " +
                                            "DATE_ADD(NOW(), INTERVAL " + days + " DAY), " +
                                            "DATE_ADD(expire_at, INTERVAL " + days + " DAY))"
                            )
            );
            if (!ok) return ApiResp.fail("更新到期时间失败");

        } else if (ac.getFunction() != null && ac.getFunction() == 2) {
            int inc = Math.max(1, ac.getValue() == null ? 0 : ac.getValue());
            boolean ok = userService.update(
                    new UpdateWrapper<User>()
                            .eq("id", uid)
                            .setSql("seat_count = IFNULL(seat_count,0) + " + inc)
            );
            if (!ok) return ApiResp.fail("增加座席失败");

        } else {
            return ApiResp.fail("未知的激活码类型(function)");
        }

        // 4) 读取用户最新状态返回（一次）
        User u = userService.getById(uid);

        RedeemResp resp = new RedeemResp();
        resp.setCodeId(ac.getId());
        resp.setCode(ac.getCode());
        resp.setFunction(ac.getFunction());
        resp.setValue(ac.getValue());
        resp.setUserId(uid);
        resp.setNewExpireAt(u != null ? u.getExpireAt() : null);
        resp.setNewSeatCount(u != null ? u.getSeatCount() : null);

        return ApiResp.ok(resp);
    }
}
