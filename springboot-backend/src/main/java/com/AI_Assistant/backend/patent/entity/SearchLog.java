package com.AI_Assistant.backend.patent.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 搜索日志实体
 */
@Data
@TableName("search_log")
public class SearchLog {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("maturity")
    private String maturity;

    @TableField("keyword")
    private String keyword;

    @TableField("search_time")
    private LocalDateTime searchTime;
}
