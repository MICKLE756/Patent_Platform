package com.AI_Assistant.backend.patent.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * 专利解绑请求DTO
 */
public class PatentUnbindRequest {

    @NotEmpty(message = "请选择要解绑的专利")
    private List<String> patentIds;

    public List<String> getPatentIds() {
        return patentIds;
    }

    public void setPatentIds(List<String> patentIds) {
        this.patentIds = patentIds;
    }
}