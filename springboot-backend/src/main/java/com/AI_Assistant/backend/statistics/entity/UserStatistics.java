package com.AI_Assistant.backend.statistics.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户统计聚合表实体
 */
@Data
@TableName("user_statistics")
public class UserStatistics {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    @TableField("stat_date")
    private LocalDate statDate;

    @TableField("total_user_count")
    private Integer totalUserCount;

    @TableField("enterprise_count")
    private Integer enterpriseCount;

    @TableField("research_team_count")
    private Integer researchTeamCount;

    @TableField("admin_count")
    private Integer adminCount;

    @TableField("new_user_count")
    private Integer newUserCount;

    @TableField("new_enterprise_count")
    private Integer newEnterpriseCount;

    @TableField("new_research_team_count")
    private Integer newResearchTeamCount;

    @TableField("active_user_count")
    private Integer activeUserCount;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
