package com.AI_Assistant.userCenter.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("username")
    private String username;

    @TableField("password")
    private String password;

    @TableField("user_type")
    private Integer userType;

    @TableField("wechat_openid")
    private String wechatOpenid;

    @TableField("wechat_unionid")
    private String wechatUnionid;

    @TableField("wechat_nickname")
    private String wechatNickname;

    @TableField("wechat_avatar")
    private String wechatAvatar;

    @TableField("contact_phone")
    private String contactPhone;

    @TableField("contact_email")
    private String contactEmail;

    @TableField("notification_addr")
    private String notificationAddr;

    @TableField("status")
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;
}