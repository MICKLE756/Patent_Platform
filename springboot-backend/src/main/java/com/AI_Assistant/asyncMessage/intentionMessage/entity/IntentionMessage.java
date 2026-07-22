package com.AI_Assistant.asyncMessage.intentionMessage.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("intention_message")
public class IntentionMessage {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("initiator_id")
    private String initiatorId;

    @TableField("initiator_type")
    private String initiatorType;

    @TableField("receiver_id")
    private String receiverId;

    @TableField("receiver_type")
    private String receiverType;

    @TableField("content")
    private String content;

    @TableField("company_address")
    private String companyAddress;

    @TableField("company_attr")
    private String companyAttr;

    @TableField("admin_status")
    private String adminStatus;

    @TableField("admin_id")
    private String adminId;

    @TableField("admin_audit_time")
    private Date adminAuditTime;

    @TableField("admin_remark")
    private String adminRemark;

    @TableField("team_status")
    private String teamStatus;

    @TableField("team_audit_time")
    private Date teamAuditTime;

    @TableField("team_remark")
    private String teamRemark;

    @TableField("send_time")
    private Date sendTime;

    @TableField("read_status")
    private Integer readStatus;

    @TableField("status")
    private Integer status;
}
