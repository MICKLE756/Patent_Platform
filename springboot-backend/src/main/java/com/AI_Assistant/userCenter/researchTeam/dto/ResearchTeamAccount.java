package com.AI_Assistant.userCenter.researchTeam.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.AI_Assistant.backend.patent.dto.PatentDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchTeamAccount {

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

    private String teamName;

    private String contactName;

    private String institution;

    private Integer teamCode;

    private String researchDomain;
    
    /**
     * 用户绑定的专利列表（登录时从数据库读取，减少前端请求）
     */
    private List<PatentDTO> patents;
}