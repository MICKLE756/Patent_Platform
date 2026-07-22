package com.AI_Assistant.backend.patent.dto;

import lombok.Data;

/**
 * 专利创建/更新请求DTO
 */
@Data
public class PatentCreateRequest {

    /**
     * 专利号/公开号（必填，新增时必填，更新时可选）
     */
    private String patentId;

    /**
     * 专利标题（必填）
     */
    private String title;

    /**
     * 申请人
     */
    private String applicant;

    /**
     * 当前权利人
     */
    private String currentOwner;

    /**
     * 发明人
     */
    private String inventors;

    /**
     * 专利类型（发明/实用新型/外观设计）
     */
    private String patentType;

    /**
     * 法律状态（授权/公开/实质审查等）
     */
    private String legalStatus;

    /**
     * 专利有效性（有效/无效/终止）
     */
    private String validity;

    /**
     * 申请日
     */
    private String applicationDate;

    /**
     * 公开日
     */
    private String publicationDate;

    /**
     * 授权公告日
     */
    private String grantDate;

    /**
     * 预估到期日
     */
    private String estimatedExpiryDate;

    /**
     * 技术摘要
     */
    private String abstractText;

    /**
     * 技术功效句
     */
    private String technicalEffectSentences;

    /**
     * 技术领域
     */
    private String techField;

    /**
     * 技术稳定性评分
     */
    private String technicalStability;

    /**
     * 技术先进性评分
     */
    private String technicalAdvancement;

    /**
     * 外部链接
     */
    private String sourceLink;
}