package com.AI_Assistant.asyncMessage.message.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
     * 消息投递状态实体类
     * 映射message_delivery表
     * 索引说明：
     * - idx_message_id: 加速按消息ID查询
     * - idx_status: 加速按状态查询（如待投递、已投递）
     * - idx_message_status: 加速消息ID+状态组合查询（用于补偿任务）
     */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("message_delivery")
public class MessageDelivery {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("message_id")
    private String messageId;

    @TableField("queue_name")
    private String queueName;

    @TableField("exchange_name")
    private String exchangeName;

    @TableField("routing_key")
    private String routingKey;

    @TableField("status")
    private String status;

    @TableField("retry_count")
    private Integer retryCount;

    @TableField("last_attempt_time")
    private LocalDateTime lastAttemptTime;

    @TableField("error_message")
    private String errorMessage;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 手动添加getter方法（解决Lombok注解处理器问题）
     */
    public Integer getRetryCount() {
        return retryCount;
    }

    /**
     * 手动添加getter方法（解决Lombok注解处理器问题）
     */
    public String getExchangeName() {
        return exchangeName;
    }

    /**
     * 手动添加builder方法（解决Lombok注解处理器问题）
     */
    public static MessageDeliveryBuilder builder() {
        return new MessageDeliveryBuilder();
    }

    /**
     * 手动实现Builder类（解决Lombok注解处理器问题）
     */
    public static class MessageDeliveryBuilder {
        private String id;
        private String messageId;
        private String queueName;
        private String exchangeName;
        private String routingKey;
        private String status;
        private Integer retryCount;
        private LocalDateTime lastAttemptTime;
        private String errorMessage;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;

        public MessageDeliveryBuilder id(String id) {
            this.id = id;
            return this;
        }

        public MessageDeliveryBuilder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public MessageDeliveryBuilder queueName(String queueName) {
            this.queueName = queueName;
            return this;
        }

        public MessageDeliveryBuilder exchangeName(String exchangeName) {
            this.exchangeName = exchangeName;
            return this;
        }

        public MessageDeliveryBuilder routingKey(String routingKey) {
            this.routingKey = routingKey;
            return this;
        }

        public MessageDeliveryBuilder status(String status) {
            this.status = status;
            return this;
        }

        public MessageDeliveryBuilder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public MessageDeliveryBuilder lastAttemptTime(LocalDateTime lastAttemptTime) {
            this.lastAttemptTime = lastAttemptTime;
            return this;
        }

        public MessageDeliveryBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public MessageDeliveryBuilder createTime(LocalDateTime createTime) {
            this.createTime = createTime;
            return this;
        }

        public MessageDeliveryBuilder updateTime(LocalDateTime updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public MessageDelivery build() {
            MessageDelivery delivery = new MessageDelivery();
            delivery.id = this.id;
            delivery.messageId = this.messageId;
            delivery.queueName = this.queueName;
            delivery.exchangeName = this.exchangeName;
            delivery.routingKey = this.routingKey;
            delivery.status = this.status;
            delivery.retryCount = this.retryCount;
            delivery.lastAttemptTime = this.lastAttemptTime;
            delivery.errorMessage = this.errorMessage;
            delivery.createTime = this.createTime;
            delivery.updateTime = this.updateTime;
            return delivery;
        }
    }
}