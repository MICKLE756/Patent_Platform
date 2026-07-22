package com.AI_Assistant.userCenter.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("role_permission")
public class RolePermission {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("role_id")
    private String roleId;

    @TableField("permission_enterprise_view")
    private Integer permissionEnterpriseView;

    @TableField("permission_enterprise_edit")
    private Integer permissionEnterpriseEdit;

    @TableField("permission_enterprise_delete")
    private Integer permissionEnterpriseDelete;

    @TableField("permission_research_view")
    private Integer permissionResearchView;

    @TableField("permission_research_edit")
    private Integer permissionResearchEdit;

    @TableField("permission_research_delete")
    private Integer permissionResearchDelete;

    @TableField("permission_patent_audit")
    private Integer permissionPatentAudit;

    @TableField("permission_patent_review")
    private Integer permissionPatentReview;

    @TableField("permission_notice_publish")
    private Integer permissionNoticePublish;

    @TableField("permission_statistics_view")
    private Integer permissionStatisticsView;

    @TableField("permission_system_config")
    private Integer permissionSystemConfig;

    @TableField("permission_intention_audit")
    private Integer permissionIntentionAudit;

    @TableField("permission_log_view")
    private Integer permissionLogView;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}