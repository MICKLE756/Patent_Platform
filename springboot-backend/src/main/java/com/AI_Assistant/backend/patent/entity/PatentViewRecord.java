package com.AI_Assistant.backend.patent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 专利浏览记录实体
 */
@Data
@TableName("patent_view_record")
public class PatentViewRecord {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField("patent_id")
    private String patentId;

    @TableField("viewer_id")
    private String viewerId;

    @TableField("viewer_type")
    private String viewerType;

    @TableField("view_time")
    private LocalDateTime viewTime;

    @TableField("stay_duration")
    private Integer stayDuration;
}
