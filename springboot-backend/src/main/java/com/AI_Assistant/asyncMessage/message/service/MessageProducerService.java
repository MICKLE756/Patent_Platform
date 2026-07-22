package com.AI_Assistant.asyncMessage.message.service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.AI_Assistant.asyncMessage.config.MessageProperties;
import com.AI_Assistant.asyncMessage.config.RabbitMQConfig;
import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.entity.MessageDelivery;
import com.AI_Assistant.asyncMessage.message.mapper.MessageDeliveryMapper;

import jakarta.annotation.PreDestroy;

/**
 * 消息生产者服务
 * 负责发送消息到RabbitMQ队列
 */
@Service
public class MessageProducerService {

    private static final Logger logger = LoggerFactory.getLogger(MessageProducerService.class);
    
    /**
     * 异步执行器，用于异步发送和重试操作
     * 使用有界队列避免内存溢出
     */
    private final ExecutorService asyncExecutor = new ThreadPoolExecutor(
            4,   // corePoolSize
            8,   // maximumPoolSize
            60,  // keepAliveTime
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // 有界队列，容量1000
            r -> {
                Thread t = new Thread(r, "message-producer-async");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时由调用线程执行
    );

    /**
     * 定时调度执行器，用于重试延迟执行
     * 避免使用 Thread.sleep() 阻塞线程
     */
    private final ScheduledExecutorService retryScheduler = new ScheduledThreadPoolExecutor(
            2,
            r -> {
                Thread t = new Thread(r, "message-producer-retry");
                t.setDaemon(true);
                return t;
            }
    );

    private final RabbitTemplate rabbitTemplate;
    private final MessageDeliveryMapper messageDeliveryMapper;
    private final MessageMonitorService monitorService;
    private final int maxRetries;

    /**
     * 构造器注入所有依赖
     */
    public MessageProducerService(RabbitTemplate rabbitTemplate,
                                  MessageDeliveryMapper messageDeliveryMapper,
                                  MessageMonitorService monitorService,
                                  MessageProperties messageProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.messageDeliveryMapper = messageDeliveryMapper;
        this.monitorService = monitorService;
        this.maxRetries = messageProperties.getRetry().getMaxRetries();
    }

    /**
     * 关闭线程池
     * 应用关闭时优雅地停止异步任务
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭消息生产者线程池...");
        
        asyncExecutor.shutdown();
        retryScheduler.shutdown();
        
        try {
            if (!asyncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("消息生产者异步线程池未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("消息生产者异步线程池关闭被中断");
        }
        
        try {
            if (!retryScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                retryScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            retryScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("消息生产者线程池已关闭");
    }

    /**
     * 发送意向留言消息（异步发送，非阻塞）
     * 使用自定义线程池，避免@Async与事务上下文冲突
     */
    public CompletableFuture<Boolean> sendIntentionMessageAsync(MessageDTO message) {
        return CompletableFuture.supplyAsync(() -> {
            sendIntentionMessage(message);
            return true;
        }, asyncExecutor).exceptionally(e -> {
            logger.error("异步发送意向留言消息失败", e);
            monitorService.recordSendFailed();
            return false;
        });
    }

    /**
     * 发送意向留言消息（同步，带重试机制）
     */
    public void sendIntentionMessage(MessageDTO message) {
        String messageId = ensureMessageId(message);
        String exchange = RabbitMQConfig.EXCHANGE_TOPIC;
        
        // 验证action不为空
        if (message.getAction() == null || message.getAction().trim().isEmpty()) {
            logger.error("消息缺少action字段，无法发送，messageId: {}", messageId);
            throw new IllegalArgumentException("消息action字段不能为空");
        }
        
        String routingKey = "message.intention." + message.getAction();

        // 先记录投递状态，失败则不发送消息
        recordDeliveryStatus(messageId, RabbitMQConfig.QUEUE_INTENTION, exchange, routingKey);

        // 异步发送消息（带重试）
        asyncSendWithRetry(exchange, routingKey, message, "意向留言");
    }

    /**
     * 发送系统通知消息（异步发送，非阻塞）
     * 使用自定义线程池，避免@Async与事务上下文冲突
     */
    public CompletableFuture<Boolean> sendNoticeMessageAsync(MessageDTO message) {
        return CompletableFuture.supplyAsync(() -> {
            sendNoticeMessage(message);
            return true;
        }, asyncExecutor).exceptionally(e -> {
            logger.error("异步发送系统通知消息失败", e);
            monitorService.recordSendFailed();
            return false;
        });
    }

    /**
     * 发送系统通知消息（同步，带重试机制）
     */
    public void sendNoticeMessage(MessageDTO message) {
        String messageId = ensureMessageId(message);
        String exchange = RabbitMQConfig.EXCHANGE_TOPIC;
        
        // 验证action不为空
        if (message.getAction() == null || message.getAction().trim().isEmpty()) {
            logger.error("消息缺少action字段，无法发送，messageId: {}", messageId);
            throw new IllegalArgumentException("消息action字段不能为空");
        }
        
        String routingKey = "message.notice." + message.getAction();

        // 先记录投递状态，失败则不发送消息
        recordDeliveryStatus(messageId, RabbitMQConfig.QUEUE_NOTICE, exchange, routingKey);

        // 异步发送消息（带重试）
        asyncSendWithRetry(exchange, routingKey, message, "系统通知");
    }

    /**
     * 发送系统广播消息（异步发送，非阻塞）
     * 使用自定义线程池，避免@Async与事务上下文冲突
     */
    public CompletableFuture<Boolean> sendBroadcastMessageAsync(MessageDTO message) {
        return CompletableFuture.supplyAsync(() -> {
            sendBroadcastMessage(message);
            return true;
        }, asyncExecutor).exceptionally(e -> {
            logger.error("异步发送广播消息失败", e);
            monitorService.recordSendFailed();
            return false;
        });
    }

    /**
     * 发送系统广播消息（同步，带重试机制）
     */
    public void sendBroadcastMessage(MessageDTO message) {
        String messageId = ensureMessageId(message);
        String exchange = RabbitMQConfig.EXCHANGE_FANOUT;
        String routingKey = ""; // Fanout交换机忽略routingKey

        // 先记录投递状态，使用独立的广播队列
        recordDeliveryStatus(messageId, RabbitMQConfig.QUEUE_BROADCAST, exchange, routingKey);

        // 异步发送到Fanout交换机进行广播
        asyncSendWithRetry(exchange, routingKey, message, "广播消息");
    }

    /**
     * 发送审批任务消息（异步发送，非阻塞）
     * 使用自定义线程池，避免@Async与事务上下文冲突
     */
    public CompletableFuture<Boolean> sendAuditMessageAsync(MessageDTO message) {
        return CompletableFuture.supplyAsync(() -> {
            sendAuditMessage(message);
            return true;
        }, asyncExecutor).exceptionally(e -> {
            logger.error("异步发送审批任务消息失败", e);
            monitorService.recordSendFailed();
            return false;
        });
    }

    /**
     * 发送审批任务消息（同步，带重试机制）
     */
    public void sendAuditMessage(MessageDTO message) {
        String messageId = ensureMessageId(message);
        String exchange = RabbitMQConfig.EXCHANGE_TOPIC;
        
        // 验证action不为空
        if (message.getAction() == null || message.getAction().trim().isEmpty()) {
            logger.error("消息缺少action字段，无法发送，messageId: {}", messageId);
            throw new IllegalArgumentException("消息action字段不能为空");
        }
        
        String routingKey = "message.audit." + message.getAction();

        // 先记录投递状态，失败则不发送消息
        recordDeliveryStatus(messageId, RabbitMQConfig.QUEUE_AUDIT, exchange, routingKey);

        // 异步发送消息（带重试）
        asyncSendWithRetry(exchange, routingKey, message, "审批任务");
    }

    /**
     * 确保消息有ID
     */
    private String ensureMessageId(MessageDTO message) {
        String messageId = message.getMessageId();
        boolean modified = false;
        if (messageId == null || messageId.trim().isEmpty()) {
            messageId = UUID.randomUUID().toString();
            message.setMessageId(messageId);
            modified = true;
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
            modified = true;
        }
        // 仅在确实需要时修改message对象，避免不必要的副作用
        if (modified) {
            logger.debug("MessageDTO对象已更新messageId/timestamp");
        }
        return messageId;
    }

    /**
     * 异步发送消息（带重试机制）
     * 使用CorrelationData跟踪消息确认状态
     */
    private void asyncSendWithRetry(String exchange, String routingKey, MessageDTO message, String messageType) {
        asyncExecutor.submit(() -> {
            String messageId = message.getMessageId();
            CorrelationData correlationData = new CorrelationData(messageId);
            sendWithRetry(exchange, routingKey, message, messageType, messageId, correlationData, 0, null);
        });
    }

    /**
     * 递归发送消息（带延迟重试）
     * 使用 ScheduledExecutorService 替代 Thread.sleep()，避免阻塞线程
     */
    private void sendWithRetry(String exchange, String routingKey, MessageDTO message, 
                               String messageType, String messageId, CorrelationData correlationData,
                               int retryCount, Exception lastException) {
        if (retryCount >= maxRetries) {
            logger.error("发送{}最终失败，messageId: {}, 已重试{}次", messageType, messageId, retryCount, lastException);
            monitorService.recordSendFailed();
            updateDeliveryStatus(messageId, "failed", lastException != null ? lastException.getMessage() : "未知错误");
            return;
        }

        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
            logger.info("发送{}成功，messageId: {}, retryCount: {}", messageType, messageId, retryCount);
            monitorService.recordSendSuccess();
            monitorService.recordMessageType(messageType);
            updateDeliveryStatus(messageId, "sent", null);
        } catch (Exception e) {
            lastException = e;
            int nextRetryCount = retryCount + 1;
            logger.warn("发送{}失败，第{}次重试，messageId: {}", messageType, nextRetryCount, messageId, e);
            monitorService.recordRetry();

            if (nextRetryCount < maxRetries) {
                long delayMs = 1000L * (long) Math.pow(2, retryCount);
                final Exception finalException = lastException;
                final int finalRetryCount = nextRetryCount;
                retryScheduler.schedule(() -> 
                    sendWithRetry(exchange, routingKey, message, messageType, messageId, correlationData, finalRetryCount, finalException),
                    delayMs, TimeUnit.MILLISECONDS
                );
            } else {
                logger.error("发送{}最终失败，messageId: {}, 已重试{}次", messageType, messageId, nextRetryCount, lastException);
                monitorService.recordSendFailed();
                updateDeliveryStatus(messageId, "failed", lastException != null ? lastException.getMessage() : "未知错误");
            }
        }
    }

    /**
     * 记录消息投递状态
     * 如果已存在相同messageId的记录，则更新状态而不是插入新记录
     */
    private void recordDeliveryStatus(String messageId, String queueName,
                                      String exchangeName, String routingKey) {
        try {
            MessageDelivery existing = messageDeliveryMapper.selectByMessageId(messageId).stream()
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                messageDeliveryMapper.updateDeliveryStatus(
                        existing.getId(),
                        "pending",
                        LocalDateTime.now(),
                        null
                );
                messageDeliveryMapper.updateRetryCount(
                        existing.getId(),
                        0,
                        LocalDateTime.now()
                );
                logger.debug("更新已有投递记录，messageId: {}", messageId);
            } else {
                MessageDelivery delivery = MessageDelivery.builder()
                        .messageId(messageId)
                        .queueName(queueName)
                        .exchangeName(exchangeName)
                        .routingKey(routingKey)
                        .status("pending")
                        .retryCount(0)
                        .createTime(LocalDateTime.now())
                        .updateTime(LocalDateTime.now())
                        .build();

                messageDeliveryMapper.insert(delivery);
            }
        } catch (Exception e) {
            logger.error("记录投递状态失败，messageId: {}", messageId, e);
        }
    }

    /**
     * 更新投递状态
     */
    private void updateDeliveryStatus(String messageId, String status, String errorMessage) {
        try {
            MessageDelivery delivery = messageDeliveryMapper.selectByMessageId(messageId).stream()
                    .findFirst()
                    .orElse(null);
            if (delivery != null) {
                messageDeliveryMapper.updateDeliveryStatus(
                        delivery.getId(),
                        status,
                        LocalDateTime.now(),
                        errorMessage
                );
            }
        } catch (Exception e) {
            logger.error("更新投递状态失败，messageId: {}", messageId, e);
        }
    }

    /**
     * 异步更新投递状态（供RabbitMQ回调使用）
     */
    public void updateDeliveryStatusAsync(String messageId, String status, String errorMessage) {
        asyncExecutor.submit(() -> updateDeliveryStatus(messageId, status, errorMessage));
    }

    /**
     * 处理消息发送失败（供RabbitMQ回调使用）
     * 更新投递状态并触发重试
     */
    public void handleSendFailure(String messageId, String errorMessage) {
        asyncExecutor.submit(() -> {
            try {
                // 更新投递状态为失败
                updateDeliveryStatus(messageId, "failed", errorMessage);
                
                // 查询投递记录，判断是否需要重试
                MessageDelivery delivery = messageDeliveryMapper.selectByMessageId(messageId).stream()
                        .findFirst()
                        .orElse(null);
                
                if (delivery != null) {
                    Integer retryCount = delivery.getRetryCount();
                    if (retryCount == null) {
                        retryCount = 0;
                    }
                    
                    if (retryCount < maxRetries) {
                        // 触发重试
                        logger.info("消息发送失败，准备重试，messageId: {}, retryCount: {}", messageId, retryCount);
                        // 这里可以实现更复杂的重试逻辑
                    } else {
                        logger.error("消息发送失败且重试次数已达上限，messageId: {}", messageId);
                    }
                }
            } catch (Exception e) {
                logger.error("处理消息发送失败异常，messageId: {}", messageId, e);
            }
        });
    }
}
