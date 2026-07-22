package com.AI_Assistant.asyncMessage.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息查询DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageQueryDTO {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 阅读状态：0-未读, 1-已读
     */
    private Integer status;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 开始时间
     */
    private String startTime;

    /**
     * 结束时间
     */
    private String endTime;

    /**
     * 页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;
}