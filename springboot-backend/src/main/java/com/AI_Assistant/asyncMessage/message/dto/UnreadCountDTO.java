package com.AI_Assistant.asyncMessage.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 未读消息数量DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountDTO {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 总未读数量
     */
    private Integer totalCount;

    /**
     * 意向留言未读数量
     */
    private Integer intentionCount;

    /**
     * 系统通知未读数量
     */
    private Integer noticeCount;

    /**
     * 审批任务未读数量
     */
    private Integer auditCount;
}