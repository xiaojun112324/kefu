package com.example.kefu.controller;

import com.example.kefu.common.ApiResp;
import com.example.kefu.dto.TransferReq;
import com.example.kefu.entity.AssignmentHistory;
import com.example.kefu.entity.Customer;
import com.example.kefu.security.AuthContext;
import com.example.kefu.security.HeaderAuthInterceptor;
import com.example.kefu.service.AssignmentHistoryService;
import com.example.kefu.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/assign")
@RequiredArgsConstructor
public class AssignmentController {

    private final CustomerService customerService;
    private final AssignmentHistoryService assignmentHistoryService;

    @PostMapping("/transfer")
    public ApiResp<Boolean> transfer(@RequestBody TransferReq req) {
        Customer c = customerService.getById(req.getCustomerId());
        if (c == null) return ApiResp.fail("customer not found");
        Long fromCs = req.getFromCsId()!=null ? req.getFromCsId() : c.getAssignedCsId();
        c.setAssignedCsId(req.getToCsId());
        customerService.updateById(c);
        AuthContext ctx = HeaderAuthInterceptor.CTX.get();
        AssignmentHistory h = new AssignmentHistory();
        h.setAgentId(c.getAgentId());
        h.setCustomerId(c.getId());
        h.setFromCsId(fromCs);
        h.setToCsId(req.getToCsId());
        h.setByUserId(ctx != null ? ctx.getUserId() : null);
        h.setReason(req.getReason());
        h.setCreatedAt(LocalDateTime.now());
        assignmentHistoryService.save(h);
        return ApiResp.ok(true);
    }
}
