package com.AI_Assistant.asyncMessage.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息传输对象
 * 用于RabbitMQ消息体和WebSocket消息传输
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    /**
     * 唯一消息ID
     */
    @Size(max = 64, message = "消息ID长度不能超过64个字符")
    private String messageId;

    /**
     * 业务类型：intention/notice/audit/system
     */
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 32, message = "业务类型长度不能超过32个字符")
    private String businessType;

    /**
     * 操作类型：create/update/approve/reject/publish
     */
    @NotBlank(message = "操作类型不能为空")
    @Size(max = 32, message = "操作类型长度不能超过32个字符")
    private String action;

    /**
     * 发送者信息
     */
    @Valid
    private Sender sender;

    /**
     * 接收者信息
     */
    @Valid
    private Receiver receiver;

    /**
     * 消息内容
     */
    @NotNull(message = "消息内容不能为空")
    @Valid
    private Content content;

    /**
     * 优先级：1-高, 2-中, 3-低
     */
    @Min(value = 1, message = "优先级最小值为1")
    @Max(value = 3, message = "优先级最大值为3")
    private Integer priority;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 发送者信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sender {
        @Size(max = 64, message = "发送者ID长度不能超过64个字符")
        private String id;
        
        @Size(max = 32, message = "发送者类型长度不能超过32个字符")
        private String type; // enterprise/research_team/admin/system
        
        @Size(max = 128, message = "发送者名称长度不能超过128个字符")
        private String name;
    }

    /**
     * 接收者信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Receiver {
        @Size(max = 64, message = "接收者ID长度不能超过64个字符")
        private String id;
        
        @Size(max = 32, message = "接收者类型长度不能超过32个字符")
        private String type; // enterprise/research_team/admin
    }

    /**
     * 消息内容
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        /**
         * 消息标题 - 允许为空（广播消息会自动填充"系统通知"默认值）
         */
        @Size(max = 256, message = "消息标题长度不能超过256个字符")
        private String title;

        @Size(max = 2000, message = "消息内容长度不能超过2000个字符")
        private String body;
        
        @Size(max = 64, message = "关联ID长度不能超过64个字符")
        private String relatedId;
        
        @Size(max = 32, message = "关联类型长度不能超过32个字符")
        private String relatedType;
    }
}