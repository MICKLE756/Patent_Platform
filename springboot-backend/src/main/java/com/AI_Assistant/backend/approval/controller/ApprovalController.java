package com.AI_Assistant.backend.approval.controller;

import com.AI_Assistant.common.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApprovalController {

    @GetMapping("/approvals/my")
    public Result<List<Map<String, Object>>> getMyApprovals() {
        return Result.ok(List.of(
            Map.of("approvalId", "APR001", "type", "patent", "status", "approved", "createTime", "2024-01-01 10:00:00"),
            Map.of("approvalId", "APR002", "type", "message", "status", "pending", "createTime", "2024-01-02 14:00:00")
        ));
    }

    @GetMapping("/approvals/{approvalId}/rejectReason")
    public Result<Map<String, Object>> getRejectReason(@PathVariable String approvalId) {
        return Result.ok(Map.of(
            "approvalId", approvalId,
            "reason", "专利信息不完整，请补充相关材料",
            "rejectTime", "2024-01-03 09:00:00"
        ));
    }
}
