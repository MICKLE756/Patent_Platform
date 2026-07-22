package com.AI_Assistant.backend.patent.dto;

import lombok.Data;

import java.util.List;

/**
 * 专利绑定结果DTO
 */
@Data
public class PatentBindResultDTO {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 绑定成功的专利ID列表
     */
    private List<String> boundPatentIds;

    /**
     * 绑定失败的专利ID列表
     */
    private List<String> failedPatentIds;

    /**
     * 消息
     */
    private String message;
}