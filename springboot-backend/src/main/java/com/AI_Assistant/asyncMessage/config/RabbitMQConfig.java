package com.AI_Assistant.asyncMessage.config;

import com.AI_Assistant.asyncMessage.message.service.MessageProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * RabbitMQ配置类
 * 
 * 队列设计：
 * - intention.q: 意向留言队列（企业→科研团队的留言）
 * - notice.q: 系统通知队列（管理员发布的系统通知）
 * - audit.q: 审批任务队列（待审核的意向留言）
 * - message.dlx.q: 死信队列（处理失败的消息）
 * 
 * 交换机设计：
 * - message.topic: Topic交换机，用于消息路由
 * - message.fanout: Fanout交换机，用于广播消息
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);
    
    private final MessageProducerService messageProducerService;

    // ==================== 队列名称常量 ====================
    public static final String QUEUE_INTENTION = "intention.q";
    public static final String QUEUE_NOTICE = "notice.q";
    public static final String QUEUE_AUDIT = "audit.q";
    public static final String QUEUE_BROADCAST = "broadcast.q";

    // ==================== 交换机名称常量 ====================
    public static final String EXCHANGE_TOPIC = "message.topic";
    public static final String EXCHANGE_FANOUT = "message.fanout";
    public static final String EXCHANGE_DEAD_LETTER = "message.dlx.exchange";

    // ==================== 路由键常量 ====================
    public static final String ROUTING_KEY_INTENTION = "message.intention.#";
    public static final String ROUTING_KEY_NOTICE = "message.notice.#";
    public static final String ROUTING_KEY_AUDIT = "message.audit.#";
    public static final String ROUTING_KEY_BROADCAST = "message.broadcast.*";

    /**
     * 消息配置属性
     */
    private final MessageProperties messageProperties;

    /**
     * 构造器注入配置属性和消息生产者服务
     * 使用 @Lazy 避免循环依赖：RabbitMQConfig → MessageProducerService → RabbitTemplate → RabbitMQConfig
     */
    public RabbitMQConfig(MessageProperties messageProperties, @Lazy MessageProducerService messageProducerService) {
        this.messageProperties = messageProperties;
        this.messageProducerService = messageProducerService;
    }

    // ==================== 死信队列配置 ====================
    /**
     * 死信队列
     * 移除固定TTL，让重试逻辑控制消息生命周期
     * 消息会在重试次数达到上限后被标记为失败并记录到数据库
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(getDeadLetterQueueName())
                .build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE_DEAD_LETTER)
                .durable(true)
                .build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(getDeadLetterQueueName());
    }

    /**
     * 获取死信队列名称
     */
    public String getDeadLetterQueueName() {
        return messageProperties.getQueue().getDeadLetterQueueName();
    }

    // ==================== 业务队列配置 ====================
    
    /**
     * 意向留言队列
     * 绑定死信交换机，消息失败时转发到死信队列
     */
    @Bean
    public Queue intentionQueue() {
        return QueueBuilder.durable(QUEUE_INTENTION)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(getDeadLetterQueueName())
                .maxLength(messageProperties.getQueue().getIntentionMaxLength())
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    /**
     * 系统通知队列
     */
    @Bean
    public Queue noticeQueue() {
        return QueueBuilder.durable(QUEUE_NOTICE)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(getDeadLetterQueueName())
                .maxLength(messageProperties.getQueue().getNoticeMaxLength())
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    /**
     * 审批任务队列
     */
    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(QUEUE_AUDIT)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(getDeadLetterQueueName())
                .maxLength(messageProperties.getQueue().getAuditMaxLength())
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    /**
     * 广播消息队列 - 独立队列，避免与通知队列混用
     */
    @Bean
    public Queue broadcastQueue() {
        return QueueBuilder.durable(QUEUE_BROADCAST)
                .deadLetterExchange(EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(getDeadLetterQueueName())
                .maxLength(messageProperties.getQueue().getBroadcastMaxLength())
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
    }

    // ==================== 交换机配置 ====================
    
    /**
     * Topic交换机 - 用于按业务类型路由消息
     */
    @Bean
    public TopicExchange topicExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_TOPIC)
                .durable(true)
                .build();
    }

    /**
     * Fanout交换机 - 用于广播消息
     */
    @Bean
    public FanoutExchange fanoutExchange() {
        return ExchangeBuilder.fanoutExchange(EXCHANGE_FANOUT)
                .durable(true)
                .build();
    }

    // ==================== 绑定配置 ====================
    
    @Bean
    public Binding intentionBinding() {
        return BindingBuilder.bind(intentionQueue())
                .to(topicExchange())
                .with(ROUTING_KEY_INTENTION);
    }

    @Bean
    public Binding noticeBinding() {
        return BindingBuilder.bind(noticeQueue())
                .to(topicExchange())
                .with(ROUTING_KEY_NOTICE);
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue())
                .to(topicExchange())
                .with(ROUTING_KEY_AUDIT);
    }

    /**
     * 广播绑定 - 使用独立的广播队列绑定到Fanout交换机
     */
    @Bean
    public Binding broadcastBinding() {
        return BindingBuilder.bind(broadcastQueue())
                .to(fanoutExchange());
    }

    // ==================== 消息转换器配置 ====================
    
    /**
     * JSON消息转换器
     * 将消息序列化为JSON格式
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter(ObjectMapper objectMapper) {
        // [已修复 - 问题101] 使用共享的ObjectMapper，确保与Redis序列化行为一致
        // 原代码：return new Jackson2JsonMessageConverter();
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /**
     * RabbitTemplate配置
     * 设置消息转换器和消息确认回调
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);  // 启用强制消息路由
        
        // 启用消息确认 - 处理消息发送到交换机后的确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            String messageId = correlationData != null && correlationData.getId() != null 
                    ? correlationData.getId() : "unknown";
            if (ack) {
                logger.debug("消息已成功投递到交换机，messageId: {}", messageId);
                messageProducerService.updateDeliveryStatusAsync(messageId, "sent", null);
            } else {
                logger.error("消息投递失败，messageId: {}, 原因: {}", messageId, cause);
                // 消息未确认，更新投递状态并触发重试
                messageProducerService.handleSendFailure(messageId, "投递到交换机失败: " + cause);
            }
        });
        
        // 启用返回消息 - 处理消息路由失败的情况
        template.setReturnsCallback(returnedMessage -> {
            String messageId = returnedMessage.getMessage().getMessageProperties().getCorrelationId();
            if (messageId == null) {
                messageId = "unknown";
            }
            String errorMsg = String.format("路由失败: exchange=%s, routingKey=%s, replyCode=%d, replyText=%s",
                    returnedMessage.getExchange(),
                    returnedMessage.getRoutingKey(),
                    returnedMessage.getReplyCode(),
                    returnedMessage.getReplyText());
            logger.error("消息路由失败，messageId: {}, {}", messageId, errorMsg);
            // 消息路由失败，更新投递状态并触发重试
            messageProducerService.handleSendFailure(messageId, errorMsg);
        });
        
        return template;
    }
}
