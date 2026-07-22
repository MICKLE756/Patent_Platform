package com.AI_Assistant.asyncMessage.message.service;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.entity.BroadcastMessage;
import com.AI_Assistant.asyncMessage.message.entity.UserMessage;
import com.AI_Assistant.asyncMessage.message.event.MessagePushListener;
import com.AI_Assistant.asyncMessage.message.mapper.BroadcastMessageMapper;
import com.AI_Assistant.asyncMessage.message.mapper.UserMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 消息处理服务
 * 负责消息的持久化和推送逻辑
 */
@Service
public class MessageProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(MessageProcessingService.class);

    private final UserMessageMapper userMessageMapper;
    private final BroadcastMessageMapper broadcastMessageMapper;
    private final MessagePushListener messagePushListener;
    private final ObjectMapper objectMapper;

    public MessageProcessingService(UserMessageMapper userMessageMapper,
                                    BroadcastMessageMapper broadcastMessageMapper,
                                    MessagePushListener messagePushListener,
                                    ObjectMapper objectMapper) {
        this.userMessageMapper = userMessageMapper;
        this.broadcastMessageMapper = broadcastMessageMapper;
        this.messagePushListener = messagePushListener;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理消息的核心逻辑（点对点消息）
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.DEFAULT)
    public void processMessage(MessageDTO message) {
        if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
            logger.warn("消息缺少消息ID，无法处理");
            return;
        }

        try {
            Integer exists = userMessageMapper.existsById(message.getMessageId());
            if (exists != null && exists > 0) {
                logger.info("消息已存在，跳过重复处理，messageId: {}", message.getMessageId());
                return;
            }
        } catch (Exception e) {
            logger.warn("消息幂等性检查失败，继续处理，messageId: {}, error: {}", message.getMessageId(), e.getMessage());
        }

        if (message.getReceiver() == null || message.getReceiver().getId() == null) {
            logger.warn("消息缺少接收者信息，无法处理，messageId: {}", message.getMessageId());
            return;
        }

        if (message.getContent() == null) {
            logger.warn("消息内容为空，无法处理，messageId: {}", message.getMessageId());
            return;
        }

        String title = message.getContent().getTitle() != null ? message.getContent().getTitle() : "";
        String body = message.getContent().getBody() != null ? message.getContent().getBody() : "";
        String relatedId = message.getContent().getRelatedId();

        UserMessage userMessage = UserMessage.builder()
                .id(message.getMessageId())
                .userId(message.getReceiver().getId())
                .messageType(message.getBusinessType())
                .subType(message.getAction())
                .title(title)
                .senderId(message.getSender() != null ? message.getSender().getId() : "system")
                .senderType(message.getSender() != null ? message.getSender().getType() : "system")
                .relatedId(relatedId)
                .priority(message.getPriority())
                .status(0)
                .createTime(LocalDateTime.now())
                .build();

        try {
            userMessage.setContent(objectMapper.writeValueAsString(message.getContent()));
        } catch (JsonProcessingException e) {
            userMessage.setContent(body);
        }

        userMessageMapper.insert(userMessage);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    boolean pushed = messagePushListener.pushMessage(message.getReceiver().getId(), message);
                    if (pushed) {
                        logger.info("消息已推送到在线用户，userId: {}", message.getReceiver().getId());
                    } else {
                        logger.info("用户离线，消息已存储到数据库，userId: {}", message.getReceiver().getId());
                    }
                } catch (Exception e) {
                    logger.error("afterCommit回调执行失败，messageId: {}", message.getMessageId(), e);
                }
            }
        });
    }

    /**
     * 处理广播消息（发给所有在线用户）
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED, isolation = Isolation.DEFAULT)
    public void processBroadcastMessage(MessageDTO message) {
        if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
            logger.warn("广播消息缺少消息ID，无法处理");
            return;
        }

        try {
            Integer exists = broadcastMessageMapper.existsById(message.getMessageId());
            if (exists != null && exists > 0) {
                logger.info("广播消息已存在，跳过重复处理，messageId: {}", message.getMessageId());
                return;
            }
        } catch (Exception e) {
            logger.warn("广播消息幂等性检查失败，继续处理，messageId: {}, error: {}", message.getMessageId(), e.getMessage());
        }

        if (message.getContent() == null) {
            logger.warn("广播消息缺少内容，无法处理，messageId: {}", message.getMessageId());
            return;
        }

        String title = message.getContent().getTitle() != null ? message.getContent().getTitle() : "系统通知";
        String body = message.getContent().getBody() != null ? message.getContent().getBody() : "";

        BroadcastMessage broadcastMessage = BroadcastMessage.builder()
                .id(message.getMessageId())
                .title(title)
                .senderId(message.getSender() != null ? message.getSender().getId() : "system")
                .senderType(message.getSender() != null ? message.getSender().getType() : "system")
                .priority(message.getPriority() != null ? message.getPriority() : 2)
                .createTime(LocalDateTime.now())
                .build();

        try {
            broadcastMessage.setContent(objectMapper.writeValueAsString(message.getContent()));
        } catch (JsonProcessingException e) {
            broadcastMessage.setContent(body);
        }

        broadcastMessageMapper.insert(broadcastMessage);
        logger.info("广播消息已存储，messageId: {}", message.getMessageId());

        messagePushListener.pushBroadcastMessage(message);
        logger.info("广播消息已推送，messageId: {}", message.getMessageId());
    }
}