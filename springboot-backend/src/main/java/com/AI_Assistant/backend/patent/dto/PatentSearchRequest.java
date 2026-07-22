package com.AI_Assistant.backend.patent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 专利搜索请求DTO
 * 支持关键词搜索和基础筛选
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatentSearchRequest {

    /**
     * 关键词（搜索标题、摘要、发明人、申请人、技术领域）
     */
    private String keyword;
    
    /**
     * 专利类型筛选（发明/实用新型/外观设计）
     */
    private String patentType;
    
    /**
     * 法律状态筛选（授权/公开/实质审查等）
     */
    private String legalStatus;
    
    /**
     * 专利有效性筛选（有效/无效/终止）
     */
    private String validity;
    
    /**
     * 页码（从1开始，默认1）
     */
    private Integer pageNum;
    
    /**
     * 每页大小（默认10）
     */
    private Integer pageSize;
}
