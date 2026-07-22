package com.AI_Assistant.userCenter.researchTeam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 绑定科研团队请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BindResearchTeamRequest {

    /**
     * 团队名称
     */
    private String teamName;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 所属单位
     */
    private String institution;

    /**
     * 团队编号
     */
    private Integer teamCode;

    /**
     * 研究方向/领域
     */
    private String researchDomain;
}