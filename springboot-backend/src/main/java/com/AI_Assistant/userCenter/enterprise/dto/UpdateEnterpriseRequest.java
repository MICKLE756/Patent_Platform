package com.AI_Assistant.userCenter.enterprise.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateEnterpriseRequest {

    private String companyName;

    private String contactName;

    private String businessLicense;

    private String companyAddress;

    private String companyIntro;
}
