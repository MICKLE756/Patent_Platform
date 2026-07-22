package com.AI_Assistant.userCenter.admin.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAccount {

    private String id;

    private String username;

    private Integer userType;

    private String wechatNickname;

    private String wechatAvatar;

    private String contactPhone;

    private String contactEmail;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime lastLoginTime;

    private String realName;

    private String roleId;
}