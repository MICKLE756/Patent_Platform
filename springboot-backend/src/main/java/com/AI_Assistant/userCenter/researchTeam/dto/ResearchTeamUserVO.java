package com.AI_Assistant.userCenter.researchTeam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 科研团队用户列表展示VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchTeamUserVO {

    /**
     * 用户ID
     */
    private String id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 微信昵称
     */
    private String wechatNickname;

    /**
     * 微信头像
     */
    private String wechatAvatar;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 团队名称
     */
    private String teamName;

    /**
     * 所属单位
     */
    private String institution;

    /**
     * 研究方向
     */
    private String researchDomain;
}