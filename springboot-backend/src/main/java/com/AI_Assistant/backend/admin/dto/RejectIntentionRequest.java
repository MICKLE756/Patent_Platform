package com.AI_Assistant.backend.admin.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 拒绝意向留言请求DTO
 */
public class RejectIntentionRequest {

    @NotBlank(message = "拒绝理由不能为空")
    private String rejectionReason;

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}