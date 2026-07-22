package com.AI_Assistant.backend.patent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专利统计数据实体
 * 统一存储专利的统计数据，支持多人共享绑定场景
 */
@Data
@TableName("patent_statistics")
public class PatentStatistics {

    /**
     * 专利ID（主键）
     */
    @TableId(value = "patent_id", type = IdType.INPUT)
    private String patentId;

    /**
     * 浏览次数
     */
    @TableField("view_count")
    private Integer viewCount;

    /**
     * 搜索次数
     */
    @TableField("search_count")
    private Integer searchCount;

    /**
     * 点击次数
     */
    @TableField("click_count")
    private Integer clickCount;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}