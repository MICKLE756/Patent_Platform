package com.AI_Assistant.userCenter.researchTeam.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新科研团队信息请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResearchTeamRequest {

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