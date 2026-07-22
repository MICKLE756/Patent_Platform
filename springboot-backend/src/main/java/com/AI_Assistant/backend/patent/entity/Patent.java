package com.AI_Assistant.backend.patent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 专利实体类
 * 字段定义完全遵循 专利详情字段.md
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("patent")
public class Patent {

    // ========== 基本标识字段 ==========
    
    /**
     * 专利号/公开号
     */
    @TableId(value = "patent_id", type = IdType.INPUT)
    private String patentId;
    
    /**
     * 专利标题
     */
    private String title;
    
    // ========== 权利人信息 ==========
    
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
    
    // ========== 法律状态信息 ==========
    
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
    
    // ========== 日期信息 ==========
    
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
    
    // ========== 技术信息 ==========
    
    /**
     * 技术摘要
     * 注：数据库列名为 abstract（Java关键字，需通过@TableField指定）
     */
    @TableField("abstract")
    private String abstractText;
    
    /**
     * 技术功效句
     */
    private String technicalEffectSentences;
    
    /**
     * 技术领域（IPC中文分类+新兴产业分类+知识密集型分类+学科分类）
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
    
    // ========== 外部链接 ==========
    
    /**
     * 外部链接（incoPat详情页）
     */
    private String sourceLink;
}
