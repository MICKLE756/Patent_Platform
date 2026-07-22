package com.AI_Assistant.asyncMessage.message.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.AI_Assistant.asyncMessage.config.MessageProperties;
import com.AI_Assistant.asyncMessage.message.entity.MessageDelivery;
import com.AI_Assistant.asyncMessage.message.mapper.MessageDeliveryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 消息补偿服务
 * 负责定时检查并重新处理失败或超时的消息
 */
@Service
public class MessageCompensationService {

    private static final Logger logger = LoggerFactory.getLogger(MessageCompensationService.class);

    private final MessageDeliveryMapper messageDeliveryMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MessageProperties messageProperties;

    public MessageCompensationService(MessageDeliveryMapper messageDeliveryMapper,
                                     RabbitTemplate rabbitTemplate,
                                     ObjectMapper objectMapper,
                                     MessageProperties messageProperties) {
        this.messageDeliveryMapper = messageDeliveryMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.messageProperties = messageProperties;
    }

    /**
     * 消息补偿定时任务
     * 每分钟检查一次需要补偿的消息
     */
    @Scheduled(fixedRate = 60000)
    public void compensateMessages() {
        logger.debug("消息补偿任务开始执行");
        
        try {
            // 处理超时未确认的消息
            handleTimeoutMessages();
            
            // 处理失败需要重试的消息
            handleFailedMessages();
            
        } catch (Exception e) {
            logger.error("消息补偿任务执行失败", e);
        }
    }

    /**
     * 处理超时未确认的消息
     * 查询状态为pending且超过超时时间的消息
     */
    private void handleTimeoutMessages() {
        int timeoutMinutes = messageProperties.getRetry().getRetryDelayMinutes();
        LocalDateTime timeoutTime = LocalDateTime.now().minusMinutes(timeoutMinutes);
        
        List<MessageDelivery> timeoutMessages = messageDeliveryMapper.selectTimeoutMessages(timeoutTime, "pending");
        
        if (timeoutMessages != null && !timeoutMessages.isEmpty()) {
            logger.info("发现 {} 条超时未确认消息", timeoutMessages.size());
            
            for (MessageDelivery delivery : timeoutMessages) {
                try {
                    // 更新状态为timeout
                    messageDeliveryMapper.updateDeliveryStatus(
                            delivery.getId(),
                            "timeout",
                            LocalDateTime.now(),
                            "消息超时未确认"
                    );
                    
                    // 尝试重新投递
                    retryMessage(delivery);
                    
                } catch (Exception e) {
                    logger.error("处理超时消息失败，messageId: {}", delivery.getMessageId(), e);
                }
            }
        }
    }

    /**
     * 处理失败需要重试的消息
     */
    private void handleFailedMessages() {
        int maxRetries = messageProperties.getRetry().getMaxRetries();
        
        List<MessageDelivery> failedMessages = messageDeliveryMapper.selectFailedMessages(maxRetries);
        
        if (failedMessages != null && !failedMessages.isEmpty()) {
            logger.info("发现 {} 条失败需要重试的消息", failedMessages.size());
            
            for (MessageDelivery delivery : failedMessages) {
                try {
                    // 增加重试次数
                    Integer currentRetryCount = delivery.getRetryCount();
                    if (currentRetryCount == null) {
                        currentRetryCount = 0;
                    }
                    
                    messageDeliveryMapper.updateRetryCount(
                            delivery.getId(),
                            currentRetryCount + 1,
                            LocalDateTime.now()
                    );
                    
                    // 尝试重新投递
                    retryMessage(delivery);
                    
                } catch (Exception e) {
                    logger.error("处理失败消息重试失败，messageId: {}", delivery.getMessageId(), e);
                }
            }
        }
    }

    /**
     * 重新投递消息
     * 由于MessageDelivery未存储消息体，此方法仅更新状态为待处理，
     * 实际的消息重新投递需要依赖其他机制
     */
    private void retryMessage(MessageDelivery delivery) {
        try {
            if (delivery.getExchangeName() == null || delivery.getRoutingKey() == null) {
                logger.warn("消息缺少路由信息，无法重新投递，messageId: {}", delivery.getMessageId());
                return;
            }
            
            // 更新状态为pending，等待消费者重新处理
            messageDeliveryMapper.updateDeliveryStatus(
                    delivery.getId(),
                    "pending",
                    LocalDateTime.now(),
                    "已触发补偿重试"
            );
            
            logger.info("消息补偿重试已触发，messageId: {}, exchange: {}, routingKey: {}", 
                    delivery.getMessageId(), 
                    delivery.getExchangeName(), 
                    delivery.getRoutingKey());
            
        } catch (Exception e) {
            logger.error("触发消息补偿重试失败，messageId: {}", delivery.getMessageId(), e);
        }
    }

    /**
     * 清理过期的消息投递记录
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredDeliveryRecords() {
        logger.debug("清理过期消息投递记录任务开始执行");
        
        try {
            int retentionDays = messageProperties.getRetry().getRetentionDays();
            LocalDateTime expireTime = LocalDateTime.now().minusDays(retentionDays);
            
            int deletedCount = messageDeliveryMapper.deleteExpiredRecords(expireTime);
            
            if (deletedCount > 0) {
                logger.info("已清理 {} 条过期消息投递记录", deletedCount);
            }
            
        } catch (Exception e) {
            logger.error("清理过期消息投递记录失败", e);
        }
    }
}
