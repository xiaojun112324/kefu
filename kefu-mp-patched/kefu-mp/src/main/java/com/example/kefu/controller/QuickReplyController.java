// src/main/java/com/xx/app/qa/controller/QuickReplyController.java
package com.example.kefu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.kefu.common.ApiResp;
import com.example.kefu.dto.QuickReplyCreateReq;
import com.example.kefu.dto.QuickReplyUpdateReq;
import com.example.kefu.entity.QuickReply;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.QuickReplyService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quick-replies")
public class QuickReplyController {

    @Resource
    private QuickReplyService quickReplyService;

    /* 查询（分页 + 关键字 msg 模糊） */
    @GetMapping
    public ApiResp<Page<QuickReply>> list(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long userId // 仅 ROOT 可指定别人
    ) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        final String role = (ctx.getRole() == null ? "" : ctx.getRole().toUpperCase());
        Long ownerId = ctx.getUserId();
        if ("ROOT".equals(role) && userId != null) {
            ownerId = userId; // 仅 ROOT 才能查询他人的快捷回复
        }

        LambdaQueryWrapper<QuickReply> qw = new LambdaQueryWrapper<>();
        qw.eq(QuickReply::getUserId, ownerId)
          .like(StringUtils.hasText(keyword), QuickReply::getMsg, keyword)
          .orderByAsc(QuickReply::getSort)
          .orderByDesc(QuickReply::getId);

        Page<QuickReply> p = quickReplyService.page(Page.of(page, size), qw);
        return ApiResp.ok(p);
    }

    /* 新增 */
    @PostMapping
    public ApiResp<Long> create(@Valid @RequestBody QuickReplyCreateReq req) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        QuickReply e = new QuickReply();
        e.setUserId(ctx.getUserId());
        e.setMsg(req.getMsg());
        e.setSort(req.getSort() == null ? 0 : req.getSort());
        quickReplyService.save(e);
        return ApiResp.ok(e.getId());
    }

    /* 修改 */
    @PutMapping("/{id}")
    public ApiResp<Boolean> update(@PathVariable Long id, @Valid @RequestBody QuickReplyUpdateReq req) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        QuickReply db = quickReplyService.getById(id);
        if (db == null) return ApiResp.fail("数据不存在");

        // 权限：ROOT 可改任意；其余必须只能改自己的
        final String role = (ctx.getRole() == null ? "" : ctx.getRole().toUpperCase());
        if (!"ROOT".equals(role) && !db.getUserId().equals(ctx.getUserId())) {
            return ApiResp.fail("FORBIDDEN");
        }

        QuickReply upd = new QuickReply();
        upd.setId(id);
        upd.setMsg(req.getMsg());
        upd.setSort(req.getSort() == null ? 0 : req.getSort());
        boolean ok = quickReplyService.updateById(upd);
        return ok ? ApiResp.ok(true) : ApiResp.fail("更新失败");
    }

    /* 删除单条 */
    @DeleteMapping("/{id}")
    public ApiResp<Boolean> delete(@PathVariable Long id) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");

        QuickReply db = quickReplyService.getById(id);
        if (db == null) return ApiResp.ok(true); // 幂等

        final String role = (ctx.getRole() == null ? "" : ctx.getRole().toUpperCase());
        if (!"ROOT".equals(role) && !db.getUserId().equals(ctx.getUserId())) {
            return ApiResp.fail("FORBIDDEN");
        }

        boolean ok = quickReplyService.removeById(id);
        return ok ? ApiResp.ok(true) : ApiResp.fail("删除失败");
    }

    /* 批量删除 */
    @DeleteMapping
    public ApiResp<Boolean> batchDelete(@RequestParam List<Long> ids) {
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        if (ctx == null || ctx.getUserId() == null) return ApiResp.fail("UNAUTHORIZED");
        if (ids == null || ids.isEmpty()) return ApiResp.ok(true);

        final String role = (ctx.getRole() == null ? "" : ctx.getRole().toUpperCase());
        if ("ROOT".equals(role)) {
            // ROOT 直接删
            quickReplyService.removeByIds(ids);
            return ApiResp.ok(true);
        }
        // 其余：只允许删除自己的记录
        boolean ok = quickReplyService.remove(
                new LambdaQueryWrapper<QuickReply>()
                        .in(QuickReply::getId, ids)
                        .eq(QuickReply::getUserId, ctx.getUserId())
        );
        return ok ? ApiResp.ok(true) : ApiResp.fail("删除失败");
    }
}
