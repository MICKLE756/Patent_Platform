package com.AI_Assistant.backend.patent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatentStatisticsDTO {

    private String patentId;
    
    private Integer viewCount;
    
    private Integer searchCount;
    
    private Integer clickCount;
}