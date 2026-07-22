package com.AI_Assistant.asyncMessage.message.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.AI_Assistant.asyncMessage.config.MessageProperties;
import com.AI_Assistant.asyncMessage.config.RabbitMQConfig;
import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.entity.MessageDelivery;
import com.AI_Assistant.asyncMessage.message.entity.UserMessage;
import com.AI_Assistant.asyncMessage.message.event.MessagePushListener;
import com.AI_Assistant.asyncMessage.message.mapper.BroadcastMessageMapper;
import com.AI_Assistant.asyncMessage.message.mapper.MessageDeliveryMapper;
import com.AI_Assistant.asyncMessage.message.mapper.UserMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

import jakarta.annotation.PreDestroy;

/**
 * 消息消费者服务
 * 负责监听RabbitMQ队列并处理消息
 */
@Service
public class MessageConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(MessageConsumerService.class);

    /**
     * 异步调度执行器，用于死信消息重试
     * 使用ScheduledExecutorService避免Thread.sleep阻塞线程
     * 使用普通线程（非daemon）确保JVM关闭时任务能正常完成
     */
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(
            4,  // corePoolSize
            r -> {
                Thread t = new Thread(r, "dead-letter-retry");
                t.setDaemon(false); // 改为普通线程，确保JVM关闭时任务能正常完成
                return t;
            }
    );

    /**
     * 退避延迟计算（指数退避）
     * @param retryCount 当前重试次数（从1开始）
     * @return 延迟毫秒数
     */
    private long calculateBackoffDelay(int retryCount) {
        // 基础延迟1秒，每次重试指数增长，最大延迟30秒
        long baseDelay = 1000L; // 1秒
        long maxDelay = 30000L; // 30秒
        long delay = baseDelay * (long) Math.pow(2, retryCount - 1);
        return Math.min(delay, maxDelay);
    }

    private final UserMessageMapper userMessageMapper;
    private final BroadcastMessageMapper broadcastMessageMapper;
    private final MessageDeliveryMapper messageDeliveryMapper;
    private final MessagePushListener messagePushListener;
    private final RateLimitService rateLimitService;
    private final MessageMonitorService monitorService;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;
    private final int maxRetries;
    private final MessageProcessingService messageProcessingService;
    // [已修复 - 问题67] 保存messageProperties用于WebSocket推送重试配置
    private final MessageProperties messageProperties;

    public MessageConsumerService(UserMessageMapper userMessageMapper,
                                  BroadcastMessageMapper broadcastMessageMapper,
                                  MessageDeliveryMapper messageDeliveryMapper,
                                  MessagePushListener messagePushListener,
                                  RateLimitService rateLimitService,
                                  MessageMonitorService monitorService,
                                  ObjectMapper objectMapper,
                                  RabbitTemplate rabbitTemplate,
                                  RabbitMQConfig rabbitMQConfig,
                                  MessageProperties messageProperties,
                                  MessageProcessingService messageProcessingService) {
        this.userMessageMapper = userMessageMapper;
        this.broadcastMessageMapper = broadcastMessageMapper;
        this.messageDeliveryMapper = messageDeliveryMapper;
        this.messagePushListener = messagePushListener;
        this.rateLimitService = rateLimitService;
        this.monitorService = monitorService;
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQConfig = rabbitMQConfig;
        this.maxRetries = messageProperties.getRetry().getMaxRetries();
        this.messageProcessingService = messageProcessingService;
        this.messageProperties = messageProperties;
    }

    /**
     * 关闭线程池
     * 应用关闭时优雅地停止重试任务
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭消息消费者重试线程池...");
        
        retryExecutor.shutdown();
        
        try {
            if (!retryExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                logger.warn("等待线程池关闭超时(60s)，正在取消待执行任务...");
                retryExecutor.shutdownNow();
                
                if (!retryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn("消息消费者重试线程池未能正常关闭，部分任务可能未完成");
                } else {
                    logger.info("消息消费者重试线程池已强制关闭");
                }
            } else {
                logger.info("消息消费者重试线程池已优雅关闭");
            }
        } catch (InterruptedException e) {
            logger.warn("线程池关闭被中断，正在强制关闭...");
            retryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 监听意向留言队列
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_INTENTION)
    public void handleIntentionMessage(Message message, Channel channel) {
        handleMessageWithAck(message, channel, "intention", RabbitMQConfig.QUEUE_INTENTION);
    }

    /**
     * 监听系统通知队列
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_NOTICE)
    public void handleNoticeMessage(Message message, Channel channel) {
        handleMessageWithAck(message, channel, "notice", RabbitMQConfig.QUEUE_NOTICE);
    }

    /**
     * 监听审核结果队列
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_AUDIT)
    public void handleAuditMessage(Message message, Channel channel) {
        handleMessageWithAck(message, channel, "audit", RabbitMQConfig.QUEUE_AUDIT);
    }

    /**
     * 监听广播消息队列
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_BROADCAST)
    public void handleBroadcastMessage(Message message, Channel channel) {
        handleMessageWithAck(message, channel, "broadcast", RabbitMQConfig.QUEUE_BROADCAST);
    }

    /**
     * 统一消息处理入口（带手动确认）
     * 处理所有类型的消息，避免重复代码
     */
    private void handleMessageWithAck(Message message, Channel channel, String messageType, String queueName) {
        long startTime = System.currentTimeMillis();
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageJson = new String(message.getBody());
        String messageId = extractMessageId(messageJson);

        try {
            // 检查是否为重试消息（避免限流-重试死循环）
            boolean isRetry = isRetryMessage(messageId, queueName);
            // 限流检查：重试消息跳过限流，避免死循环
            if (!isRetry && !rateLimitService.allow(messageType)) {
                logger.warn("消息限流，messageType: {}, messageId: {}", messageType, messageId);
                // 拒绝消息，让它进入死信队列等待重试（requeue=false表示不重新入队原队列）
                basicRejectOrNack(channel, deliveryTag);
                return;
            }

            // 解析消息
            MessageDTO messageDTO = objectMapper.readValue(messageJson, MessageDTO.class);

            // 验证消息ID
            if (messageDTO.getMessageId() == null || messageDTO.getMessageId().trim().isEmpty()) {
                logger.warn("消息缺少消息ID，无法处理，messageType: {}", messageType);
                updateDeliveryStatusByQueue(messageId, queueName, "failed", "消息ID为空");
                basicRejectOrNack(channel, deliveryTag);
                return;
            }

            // 根据消息类型处理
            if ("notice".equals(messageType) && messageDTO.getReceiver() == null) {
                // 广播消息
                messageProcessingService.processBroadcastMessage(messageDTO);
            } else {
                // 点对点消息
                messageProcessingService.processMessage(messageDTO);
            }

            logger.info("处理消息成功，messageType: {}, messageId: {}, duration: {}ms",
                    messageType, maskMessageId(messageDTO.getMessageId()), System.currentTimeMillis() - startTime);
            
            // [已修复 - 问题58] 消息处理成功后更新投递状态为delivered
            // 原代码：只确认消息，没有更新投递状态，导致状态不一致
            updateDeliveryStatus(messageDTO.getMessageId(), "delivered", null);
            
            // 记录监控指标
            monitorService.recordConsumeSuccess();
            monitorService.recordMessageType(messageType);
            monitorService.recordConsumeDuration(System.currentTimeMillis() - startTime);
            
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            
        } catch (JsonProcessingException e) {
            logger.error("解析消息失败，messageType: {}, messageJson: {}", messageType, messageJson, e);
            updateDeliveryStatusByQueue(messageId, queueName, "failed", "消息解析失败: " + e.getMessage());
            monitorService.recordConsumeFailed();
            basicRejectOrNack(channel, deliveryTag);
        } catch (Exception e) {
            logger.error("处理消息异常，messageType: {}, messageId: {}", messageType, messageId, e);
            updateDeliveryStatusByQueue(messageId, queueName, "failed", e.getMessage());
            monitorService.recordConsumeFailed();
            basicRejectOrNack(channel, deliveryTag);
        }
    }

    /**
     * 拒绝消息并重新入队（用于可重试的错误）
     * 增加channel状态检查，避免在channel已关闭时无限循环
     */
    private void basicRejectOrNack(Channel channel, long deliveryTag) {
        if (channel == null || !channel.isOpen()) {
            logger.warn("Channel已关闭，无法拒绝消息，deliveryTag: {}", deliveryTag);
            return;
        }
        try {
            // 拒绝消息并重新入队（会触发死信队列）
            channel.basicReject(deliveryTag, false);
        } catch (IOException e) {
            // channel可能已关闭，记录日志但不再抛出
            logger.error("拒绝消息失败，deliveryTag: {}, channel可能已关闭", deliveryTag, e);
        } catch (Exception e) {
            // 捕获所有异常，避免影响后续处理
            logger.error("拒绝消息时发生未知异常，deliveryTag: {}", deliveryTag, e);
        }
    }

    /**
     * 监听死信队列（带手动确认）
     */
    @RabbitListener(queues = "#{@rabbitMQConfig.getDeadLetterQueueName()}")
    public void handleDeadLetterMessage(Message message, Channel channel) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageJson = new String(message.getBody());
        
        logger.warn("收到死信队列消息: {}", messageJson);
        monitorService.recordDeadLetter();
        
        try {
            MessageDTO messageDTO = objectMapper.readValue(messageJson, MessageDTO.class);
            
            // 查询投递状态，判断是否需要重试
            List<MessageDelivery> deliveries = messageDeliveryMapper.selectByMessageId(messageDTO.getMessageId());
            
            if (deliveries.isEmpty()) {
                logger.error("死信消息未找到对应的投递记录，messageId: {}", messageDTO.getMessageId());
                channel.basicAck(deliveryTag, false); // 确认消息
                return;
            }
            
            MessageDelivery delivery = deliveries.get(0);
            
            // 检查重试次数是否未超过限制（处理getRetryCount可能返回null的情况）
            Integer retryCount = delivery.getRetryCount();
            if (retryCount == null) {
                retryCount = 0;
            }
            if (retryCount < maxRetries) {
                // 异步重试发送消息（带指数退避）
                asyncRetryMessageWithBackoff(messageDTO, delivery);
            } else {
                logger.error("消息重试次数已达上限，不再重试，messageId: {}", messageDTO.getMessageId());
                updateDeliveryStatus(messageDTO.getMessageId(), "failed", "重试次数已达上限");
            }
            
            // 确认死信消息已处理
            channel.basicAck(deliveryTag, false);
            
        } catch (JsonProcessingException e) {
            logger.error("解析死信消息失败，无法追踪messageId，原始消息: {}", messageJson, e);
            // [已修复 - 问题53] 解析失败时先更新投递状态为failed，再确认消息
            // 原代码：直接确认消息，没有更新投递状态，导致消息丢失
            String failedMessageId = extractMessageId(messageJson);
            if (failedMessageId != null) {
                updateDeliveryStatus(failedMessageId, "failed", "死信消息解析失败: " + e.getMessage());
            }
            // 确认消息，避免死循环
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException ex) {
                logger.error("确认死信消息失败，deliveryTag: {}", deliveryTag, ex);
            }
        } catch (IOException e) {
            logger.error("确认死信消息失败，deliveryTag: {}", deliveryTag, e);
        }
    }

    /**
     * 异步重试发送消息（带指数退避）
     */
    private void asyncRetryMessageWithBackoff(MessageDTO message, MessageDelivery delivery) {
        Integer currentRetryCount = delivery.getRetryCount();
        if (currentRetryCount == null) {
            currentRetryCount = 0;
        }
        int retryCount = currentRetryCount + 1;
        long delay = calculateBackoffDelay(retryCount);
        
        logger.info("消息将在 {}ms 后重试，messageId: {}, retryCount: {}", delay, message.getMessageId(), retryCount);
        
        // 使用schedule延迟执行，避免Thread.sleep阻塞线程
        retryExecutor.schedule(() -> {
            try {
                retryMessage(message, delivery);
            } catch (Exception e) {
                logger.error("异步重试消息失败，messageId: {}", message.getMessageId(), e);
                // 更新投递状态为失败
                updateDeliveryStatus(message.getMessageId(), "failed", e.getMessage());
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * 重试发送消息
     */
    private void retryMessage(MessageDTO message, MessageDelivery delivery) {
        long startTime = System.currentTimeMillis();
        try {
            // 根据原始队列重新发送消息
            String exchangeName = delivery.getExchangeName();
            String routingKey = delivery.getRoutingKey();
            
            // [已修复 - 问题55] 调整顺序：先更新状态为重试中，再更新重试次数
            // 使用原子更新方法同时更新状态和重试次数，避免高并发下数据不一致
            Integer currentRetryCount = delivery.getRetryCount();
            int newRetryCount = (currentRetryCount != null ? currentRetryCount : 0) + 1;
            
            // 使用原子更新方法（问题65）
            messageDeliveryMapper.updateStatusAndRetryCount(
                    delivery.getId(),
                    "retrying",
                    newRetryCount,
                    LocalDateTime.now(),
                    null
            );
            
            // 重新发送消息
            rabbitTemplate.convertAndSend(exchangeName, routingKey, message);
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("消息已重试发送，messageId: {}, retryCount: {}, duration: {}ms", 
                    message.getMessageId(), newRetryCount, duration);
            
            // 记录重试指标
            monitorService.recordRetry();
            monitorService.recordRetryDuration(duration);
            
        } catch (Exception e) {
            logger.error("消息重试发送失败，messageId: {}", message.getMessageId(), e);
            updateDeliveryStatus(message.getMessageId(), "failed", e.getMessage());
        }
    }

    /**
     * 推送消息（带重试机制）
     * @param userId 用户ID
     * @param message 消息内容
     * @return 是否推送成功（仅表示第一次尝试结果）
     */
    private boolean pushMessageWithRetry(String userId, MessageDTO message) {
        // [已修复 - 问题67] 使用配置文件中的重试参数，而非硬编码
        int maxRetries = messageProperties.getRetry().getWebSocketMaxRetries();
        long backoffDelay = messageProperties.getRetry().getWebSocketRetryDelayMs();
        
        try {
            if (messagePushListener.pushMessage(userId, message)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            logger.warn("消息推送失败，准备异步重试，userId: {}, messageId: {}", userId, message.getMessageId(), e);
            scheduleRetry(userId, message, 1, maxRetries, backoffDelay);
            return false;
        }
    }

    private void scheduleRetry(String userId, MessageDTO message, int currentRetry, int maxRetries, long backoffDelay) {
        if (currentRetry >= maxRetries) {
            logger.error("消息推送多次失败，已放弃重试，userId: {}, messageId: {}", userId, message.getMessageId());
            // [已修复 - 问题57] 重试达到上限时更新投递状态为failed
            // 原代码：只记录日志，没有更新状态，导致该消息无法被定时补偿任务发现
            updateDeliveryStatus(message.getMessageId(), "failed", "WebSocket推送重试次数已达上限");
            return;
        }
        
        long delay = backoffDelay * currentRetry;
        retryExecutor.schedule(() -> {
            try {
                if (messagePushListener.pushMessage(userId, message)) {
                    logger.info("消息重试推送成功，userId: {}, messageId: {}, retryCount: {}", 
                            userId, message.getMessageId(), currentRetry);
                    updateDeliveryStatus(message.getMessageId(), "delivered", null);
                } else {
                    logger.warn("消息重试推送失败，用户离线，userId: {}, messageId: {}, retryCount: {}", 
                            userId, message.getMessageId(), currentRetry);
                }
            } catch (Exception e) {
                logger.warn("消息重试失败，userId: {}, messageId: {}, retryCount: {}", 
                        userId, message.getMessageId(), currentRetry, e);
                scheduleRetry(userId, message, currentRetry + 1, maxRetries, backoffDelay);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    // /**
    //  * 处理消息的核心逻辑（点对点消息）
    //  * 注意：必须为public方法才能被Spring事务代理
    //  * 已移至 MessageProcessingService 中，此处保留仅作参考
    //  */
    // @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    // public void processMessage(MessageDTO message) {
    //     // 验证消息ID
    //     if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
    //         logger.warn("消息缺少消息ID，无法处理");
    //         return;
    //     }
    //
    //     // 幂等性检查：如果消息已存在，跳过处理
    //     try {
    //         Integer exists = userMessageMapper.existsById(message.getMessageId());
    //         if (exists != null && exists > 0) {
    //             logger.info("消息已存在，跳过重复处理，messageId: {}", message.getMessageId());
    //             updateDeliveryStatus(message.getMessageId(), "delivered", null);
    //             return;
    //         }
    //     } catch (Exception e) {
    //         logger.warn("消息幂等性检查失败，继续处理，messageId: {}, error: {}", message.getMessageId(), e.getMessage());
    //     }
    //
    //     // 验证接收者信息
    //     if (message.getReceiver() == null || message.getReceiver().getId() == null) {
    //         logger.warn("消息缺少接收者信息，无法处理，messageId: {}", message.getMessageId());
    //         updateDeliveryStatus(message.getMessageId(), "failed", "缺少接收者信息");
    //         return;
    //     }
    //
    //     // 验证消息内容
    //     if (message.getContent() == null) {
    //         logger.warn("消息内容为空，无法处理，messageId: {}", message.getMessageId());
    //         updateDeliveryStatus(message.getMessageId(), "failed", "消息内容为空");
    //         return;
    //     }
    //
    //     // 1. 持久化消息到数据库
    //     String title = message.getContent().getTitle() != null ? message.getContent().getTitle() : "";
    //     String body = message.getContent().getBody() != null ? message.getContent().getBody() : "";
    //     String relatedId = message.getContent().getRelatedId();
    //
    //     UserMessage userMessage = UserMessage.builder()
    //             .id(message.getMessageId())
    //             .userId(message.getReceiver().getId())
    //             .messageType(message.getBusinessType())
    //             .subType(message.getAction())
    //             .title(title)
    //             .senderId(message.getSender() != null ? message.getSender().getId() : "system")
    //             .senderType(message.getSender() != null ? message.getSender().getType() : "system")
    //             .relatedId(relatedId)
    //             .priority(message.getPriority())
    //             .status(0)
    //             .createTime(LocalDateTime.now())
    //             .build();
    //
    //     try {
    //         userMessage.setContent(objectMapper.writeValueAsString(message.getContent()));
    //     } catch (JsonProcessingException e) {
    //         userMessage.setContent(body);
    //     }
    //
    //     userMessageMapper.insert(userMessage);
    //
    //     // 2. 事务提交后通过WebSocket推送
    //     TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    //         @Override
    //         public void afterCommit() {
    //             try {
    //                 // 事务提交成功后再推送，避免推送成功但事务回滚的不一致
    //                 boolean pushed = pushMessageWithRetry(message.getReceiver().getId(), message);
    //                 if (pushed) {
    //                     logger.info("消息已推送到在线用户，userId: {}", message.getReceiver().getId());
    //                     // 推送成功，更新投递状态为已投递
    //                     updateDeliveryStatus(message.getMessageId(), "delivered", null);
    //                 } else {
    //                     logger.info("用户离线，消息已存储到数据库，userId: {}", message.getReceiver().getId());
    //                     // 推送失败（用户离线），更新投递状态为待投递，等待用户上线后补偿
    //                     updateDeliveryStatus(message.getMessageId(), "pending", "用户离线");
    //                 }
    //             } catch (Exception e) {
    //                 // 捕获所有异常，避免影响后续操作
    //                 logger.error("afterCommit回调执行失败，messageId: {}", message.getMessageId(), e);
    //                 // 回调执行失败，更新投递状态为待投递，等待补偿任务重试
    //                 updateDeliveryStatus(message.getMessageId(), "pending", "afterCommit回调异常: " + e.getMessage());
    //             }
    //         }
    //     });
    // }

    // /**
    //  * 处理广播消息（发给所有在线用户）
    //  * 注意：必须为public方法才能被Spring事务代理
    //  * 已移至 MessageProcessingService 中，此处保留仅作参考
    //  */
    // @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT)
    // public void processBroadcastMessage(MessageDTO message) {
    //     // 验证消息ID
    //     if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
    //         logger.warn("广播消息缺少消息ID，无法处理");
    //         return;
    //     }
    //
    //     // 幂等性检查：如果消息已存在，跳过处理
    //     try {
    //         Integer exists = broadcastMessageMapper.existsById(message.getMessageId());
    //         if (exists != null && exists > 0) {
    //             logger.info("广播消息已存在，跳过重复处理，messageId: {}", message.getMessageId());
    //             updateDeliveryStatus(message.getMessageId(), "delivered", null);
    //             return;
    //         }
    //     } catch (Exception e) {
    //         logger.warn("广播消息幂等性检查失败，继续处理，messageId: {}, error: {}", message.getMessageId(), e.getMessage());
    //     }
    //
    //     // 验证消息内容
    //     if (message.getContent() == null) {
    //         logger.warn("广播消息缺少内容，无法处理，messageId: {}", message.getMessageId());
    //         updateDeliveryStatus(message.getMessageId(), "failed", "消息内容为空");
    //         return;
    //     }
    //
    //     // 提取消息内容，避免重复调用
    //     String title = message.getContent().getTitle() != null ? message.getContent().getTitle() : "系统通知";
    //     String body = message.getContent().getBody() != null ? message.getContent().getBody() : "";
    //     
    //     // 存储广播消息到独立的广播消息表
    //     BroadcastMessage broadcastMessage = BroadcastMessage.builder()
    //             .id(message.getMessageId())
    //             .title(title)
    //             .senderId(message.getSender() != null ? message.getSender().getId() : "system")
    //             .senderType(message.getSender() != null ? message.getSender().getType() : "system")
    //             .priority(message.getPriority() != null ? message.getPriority() : 2)
    //             .createTime(LocalDateTime.now())
    //             .build();
    //
    //     try {
    //         broadcastMessage.setContent(objectMapper.writeValueAsString(message.getContent()));
    //     } catch (JsonProcessingException e) {
    //         broadcastMessage.setContent(body);
    //     }
    //
    //     try {
    //         broadcastMessageMapper.insert(broadcastMessage);
    //         logger.info("广播消息已存储，messageId: {}", message.getMessageId());
    //         // 更新投递状态为已投递
    //         updateDeliveryStatus(message.getMessageId(), "delivered", null);
    //     } catch (Exception e) {
    //         logger.error("存储广播消息失败，messageId: {}", message.getMessageId(), e);
    //         updateDeliveryStatus(message.getMessageId(), "failed", e.getMessage());
    //         throw e;
    //     }
    //
    //     // 通过WebSocket推送给所有在线用户
    //     messagePushListener.pushBroadcastMessage(message);
    //     
    //     logger.info("广播消息已推送，messageId: {}", message.getMessageId());
    // }

    /**
     * 更新消息投递状态
     * 使用按messageId批量更新，避免循环查询，提高性能
     */
    private void updateDeliveryStatus(String messageId, String status, String errorMessage) {
        if (messageId == null || messageId.trim().isEmpty()) {
            logger.warn("更新投递状态失败：messageId为空");
            return;
        }

        // [已修复 - 问题66] 状态转换约束校验
        // 检查目标状态是否合法
        if (!com.AI_Assistant.asyncMessage.message.constant.DeliveryStatus.isValid(status)) {
            logger.warn("更新投递状态失败：非法状态值，status: {}", status);
            return;
        }

        try {
            // [已修复 - 问题66] 查询当前状态，检查状态转换是否合法
            List<MessageDelivery> deliveries = messageDeliveryMapper.selectByMessageId(messageId);
            if (deliveries != null && !deliveries.isEmpty()) {
                for (MessageDelivery delivery : deliveries) {
                    String currentStatus = delivery.getStatus();
                    if (!com.AI_Assistant.asyncMessage.message.constant.DeliveryStatus.isTransitionAllowed(currentStatus, status)) {
                        logger.warn("更新投递状态失败：非法状态转换，messageId: {}, currentStatus: {}, targetStatus: {}", 
                                messageId, currentStatus, status);
                        // 跳过非法转换，但继续检查其他投递记录
                        continue;
                    }
                }
            }

            // 按messageId批量更新，避免循环查询
            int updated = messageDeliveryMapper.updateDeliveryStatusByMessageId(
                    messageId,
                    status,
                    LocalDateTime.now(),
                    errorMessage
            );
            if (updated == 0) {
                logger.debug("未找到messageId对应的投递记录，messageId: {}", messageId);
            }
        } catch (Exception e) {
            logger.error("更新投递状态失败，messageId: {}", messageId, e);
        }
    }

    /**
     * 根据队列名称更新投递状态（用于消息ID解析失败的情况）
     */
    private void updateDeliveryStatusByQueue(String messageId, String queueName, String status, String errorMessage) {
        if (messageId == null || messageId.trim().isEmpty()) {
            logger.warn("根据队列更新投递状态失败：messageId为空");
            return;
        }
        
        try {
            MessageDelivery delivery = messageDeliveryMapper.selectByMessageIdAndQueue(messageId, queueName);
            if (delivery != null) {
                messageDeliveryMapper.updateDeliveryStatus(
                        delivery.getId(),
                        status,
                        LocalDateTime.now(),
                        errorMessage
                );
            } else {
                logger.warn("未找到投递记录，messageId: {}, queueName: {}", messageId, queueName);
            }
        } catch (Exception e) {
            logger.error("根据队列更新投递状态失败，messageId: {}, queueName: {}", messageId, queueName, e);
        }
    }

    /**
     * 预编译的messageId提取正则
     */
    private static final Pattern MESSAGE_ID_PATTERN = Pattern.compile("\"messageId\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * 对messageId进行脱敏处理，只显示前4位和后4位
     * 避免在日志中泄露完整用户信息
     */
    private String maskMessageId(String messageId) {
        if (messageId == null || messageId.length() <= 8) {
            return "****";
        }
        return messageId.substring(0, 4) + "****" + messageId.substring(messageId.length() - 4);
    }

    /**
     * 检查是否为重试消息
     * 通过查询投递记录的retryCount判断
     */
    private boolean isRetryMessage(String messageId, String queueName) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }
        try {
            List<MessageDelivery> deliveries = messageDeliveryMapper.selectByMessageId(messageId);
            if (deliveries != null) {
                for (MessageDelivery delivery : deliveries) {
                    Integer retryCount = delivery.getRetryCount();
                    if (retryCount != null && retryCount > 0) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("检查重试状态失败，messageId: {}", messageId, e);
        }
        return false;
    }

    /**
     * 从JSON字符串中提取消息ID
     */
    private String extractMessageId(String messageJson) {
        if (messageJson == null || messageJson.isEmpty()) {
            return null;
        }
        try {
            Matcher matcher = MESSAGE_ID_PATTERN.matcher(messageJson);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            logger.warn("提取消息ID失败", e);
        }
        return null;
    }

    /**
     * 定时任务：检查并重试失败的消息
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void retryFailedMessages() {
        try {
            // [已修复 - 问题56] 修改查询逻辑：同时查询pending和retrying状态的消息
            // 原代码：只查询failed状态，但注释说查询pending，逻辑不一致
            List<MessageDelivery> needRetry = messageDeliveryMapper.selectNeedRetry(maxRetries);
            if (needRetry != null && !needRetry.isEmpty()) {
                logger.info("发现 {} 条需要补偿的消息", needRetry.size());
                for (MessageDelivery delivery : needRetry) {
                    try {
                        // [已修复 - 问题56] 使用原子更新方法同时更新状态和重试次数
                        Integer currentRetryCount = delivery.getRetryCount();
                        if (currentRetryCount == null) {
                            currentRetryCount = 0;
                        }
                        int newRetryCount = currentRetryCount + 1;
                        
                        messageDeliveryMapper.updateStatusAndRetryCount(
                                delivery.getId(),
                                "retrying",
                                newRetryCount,
                                LocalDateTime.now(),
                                null
                        );

                        // [已修复 - 问题56] 先查询广播消息，再查询用户消息
                        // 原代码：只查询userMessage表，没有查询广播消息
                        boolean isBroadcast = false;
                        
                        // 尝试查询广播消息
                        // BroadcastMessage broadcastMessage = broadcastMessageMapper.selectById(delivery.getMessageId());
                        // if (broadcastMessage != null) {
                        //     isBroadcast = true;
                        //     // 广播消息重新推送
                        //     MessageDTO broadcastDTO = MessageDTO.builder()
                        //             .messageId(broadcastMessage.getId())
                        //             .businessType("notice")
                        //             .build();
                        //     messagePushListener.pushBroadcastMessage(broadcastDTO);
                        //     updateDeliveryStatus(delivery.getMessageId(), "delivered", null);
                        //     continue;
                        // }

                        // 2. 查询用户消息
                        UserMessage userMessage = userMessageMapper.selectById(delivery.getMessageId());
                        if (userMessage == null) {
                            logger.warn("补偿消息失败：消息不存在，messageId: {}", delivery.getMessageId());
                            updateDeliveryStatus(delivery.getMessageId(), "failed", "消息不存在");
                            continue;
                        }

                        // 3. 重新构造MessageDTO
                        MessageDTO messageDTO = MessageDTO.builder()
                                .messageId(userMessage.getId())
                                .businessType(userMessage.getMessageType())
                                .action(userMessage.getSubType())
                                .sender(MessageDTO.Sender.builder()
                                        .id(userMessage.getSenderId())
                                        .type(userMessage.getSenderType())
                                        .build())
                                .receiver(MessageDTO.Receiver.builder()
                                        .id(userMessage.getUserId())
                                        .build())
                                .priority(userMessage.getPriority())
                                .build();

                        // 4. 重新尝试WebSocket推送
                        boolean pushed = pushMessageWithRetry(userMessage.getUserId(), messageDTO);
                        if (pushed) {
                            logger.info("补偿消息推送成功，messageId: {}, userId: {}, retryCount: {}",
                                    delivery.getMessageId(), userMessage.getUserId(), newRetryCount);
                            updateDeliveryStatus(delivery.getMessageId(), "delivered", null);
                        } else {
                            logger.warn("补偿消息推送失败，用户仍离线，messageId: {}, userId: {}, retryCount: {}",
                                    delivery.getMessageId(), userMessage.getUserId(), newRetryCount);
                            // 用户仍离线，更新状态为pending等待下次补偿
                            updateDeliveryStatus(delivery.getMessageId(), "pending", "用户离线");
                        }

                    } catch (Exception e) {
                        logger.error("补偿消息失败，messageId: {}", delivery.getMessageId(), e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("消息补偿定时任务执行失败", e);
        }
    }
}