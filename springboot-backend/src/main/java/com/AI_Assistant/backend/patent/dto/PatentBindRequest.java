package com.AI_Assistant.backend.patent.dto;

import lombok.Data;

import java.util.List;

/**
 * 专利绑定请求DTO
 */
@Data
public class PatentBindRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户类型: enterprise/research_team
     */
    private String userType;

    /**
     * 待绑定的专利ID列表
     */
    private List<String> patentIds;
}