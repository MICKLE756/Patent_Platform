package com.AI_Assistant.userCenter.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

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
}