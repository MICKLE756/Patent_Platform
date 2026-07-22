package com.AI_Assistant.userCenter.user.dto;

import java.io.Serializable;
import java.util.List;

import com.AI_Assistant.backend.patent.dto.PatentDTO;
import com.AI_Assistant.userCenter.enterprise.dto.EnterpriseAccount;
import com.AI_Assistant.userCenter.researchTeam.dto.ResearchTeamAccount;
import com.AI_Assistant.userCenter.user.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完整用户信息DTO
 * 用于缓存用户的所有信息，包括基础用户信息和企业/科研团队额外信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFullInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 基础用户信息
     */
    private User user;

    /**
     * 企业账户信息（仅企业用户有值）
     */
    private EnterpriseAccount enterpriseAccount;

    /**
     * 科研团队账户信息（仅科研团队用户有值）
     */
    private ResearchTeamAccount researchTeamAccount;

    /**
     * 已绑定专利ID列表（用于快速判断专利绑定状态）
     */
    private List<String> boundPatentIds;

    /**
     * 已绑定专利详情列表
     */
    private List<PatentDTO> boundPatents;
}
