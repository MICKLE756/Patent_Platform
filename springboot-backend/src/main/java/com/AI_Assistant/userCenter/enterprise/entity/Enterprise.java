package com.AI_Assistant.userCenter.enterprise.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("enterprise")
public class Enterprise {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("company_name")
    private String companyName;

    @TableField("contact_name")
    private String contactName;

    @TableField("business_license")
    private String businessLicense;

    @TableField("company_intro")
    private String companyIntro;

    @TableField("company_address")
    private String companyAddress;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}