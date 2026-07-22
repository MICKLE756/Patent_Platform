package com.AI_Assistant.userCenter.enterprise.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseUserVO {

    private String id;

    private String username;

    private String wechatNickname;

    private String wechatAvatar;

    private String companyName;

    private Integer status;
}
