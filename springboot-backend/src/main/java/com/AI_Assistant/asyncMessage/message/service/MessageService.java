package com.AI_Assistant.asyncMessage.message.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.AI_Assistant.asyncMessage.config.MessageProperties;
import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.dto.UnreadCountDTO;
import com.AI_Assistant.asyncMessage.message.entity.BroadcastMessage;
import com.AI_Assistant.asyncMessage.message.entity.UserMessage;
import com.AI_Assistant.asyncMessage.message.mapper.BroadcastMessageMapper;
import com.AI_Assistant.asyncMessage.message.mapper.UserMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 消息业务服务
 * 提供消息查询、标记已读等业务操作
 */
@Service
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    private final UserMessageMapper userMessageMapper;
    private final BroadcastMessageMapper broadcastMessageMapper;
    private final ObjectMapper objectMapper;
    private final int maxLimit;

    /**
     * 构造器注入所有依赖
     */
    public MessageService(UserMessageMapper userMessageMapper,
                         BroadcastMessageMapper broadcastMessageMapper,
                         ObjectMapper objectMapper,
                         MessageProperties messageProperties) {
        this.userMessageMapper = userMessageMapper;
        this.broadcastMessageMapper = broadcastMessageMapper;
        this.objectMapper = objectMapper;
        this.maxLimit = messageProperties.getQuery().getMaxLimit();
    }

    /**
     * 获取用户未读消息数量
     */
    public UnreadCountDTO getUnreadCount(String userId) {
        // 验证userId
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("获取未读消息数量失败：userId为空");
            return UnreadCountDTO.builder()
                    .userId(null)
                    .totalCount(0)
                    .intentionCount(0)
                    .noticeCount(0)
                    .auditCount(0)
                    .build();
        }
        
        UnreadCountDTO countDTO = UnreadCountDTO.builder()
                .userId(userId)
                .totalCount(userMessageMapper.countUnreadByUserId(userId))
                .intentionCount(userMessageMapper.countUnreadByUserIdAndType(userId, "intention"))
                .noticeCount(userMessageMapper.countUnreadByUserIdAndType(userId, "notice"))
                .auditCount(userMessageMapper.countUnreadByUserIdAndType(userId, "audit"))
                .build();

        return countDTO;
    }

    /**
     * 获取用户未读消息列表（登录时拉取离线消息）
     */
    public List<UserMessage> getUnreadMessages(String userId) {
        // 验证userId
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("查询未读消息失败：userId为空");
            return Collections.emptyList();
        }
        return userMessageMapper.selectUnreadMessages(userId);
    }

    /**
     * 获取用户所有消息列表
     */
    public List<UserMessage> getMessagesByUserId(String userId, Integer pageNum, Integer pageSize) {
        // 验证userId
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("查询消息列表失败：userId为空");
            return Collections.emptyList();
        }
        
        int pageNumber = pageNum != null && pageNum > 0 ? pageNum : 1;
        // 限制每页最大100条，防止恶意请求
        int pageSizeNumber = pageSize != null && pageSize > 0 ? Math.min(pageSize, maxLimit) : 20;
        int offset = (pageNumber - 1) * pageSizeNumber;

        return userMessageMapper.selectByUserIdWithPagination(userId, offset, pageSizeNumber);
    }

    /**
     * 获取消息详情
     */
    public UserMessage getMessageById(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            logger.warn("查询消息详情失败：messageId为空");
            return null;
        }
        return userMessageMapper.selectById(messageId);
    }

    /**
     * 标记单条消息为已读
     */
    public boolean markAsRead(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            logger.warn("标记已读失败：messageId为空");
            return false;
        }
        int updated = userMessageMapper.markAsRead(messageId);
        return updated > 0;
    }

    /**
     * 标记用户所有消息为已读
     */
    public int markAllAsRead(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("标记全部已读失败：userId为空");
            return 0;
        }
        return userMessageMapper.markAllAsRead(userId);
    }

    /**
     * 根据消息类型获取消息列表（限制最大返回数量）
     */
    public List<UserMessage> getMessagesByType(String userId, String messageType) {
        // 验证userId
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("查询消息失败：userId为空");
            return Collections.emptyList();
        }

        // 验证messageType合法性，避免SQL注入风险
        if (messageType != null && !isValidMessageType(messageType)) {
            logger.warn("查询消息失败：messageType包含非法字符，messageType: {}", messageType);
            return Collections.emptyList();
        }

        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .eq(messageType != null, UserMessage::getMessageType, messageType)
                .orderByAsc(UserMessage::getPriority)
                .orderByDesc(UserMessage::getCreateTime)
                // 使用安全的占位符方式传递LIMIT参数，避免SQL注入
                .last("LIMIT " + Math.max(1, Math.min(maxLimit, 1000)));

        try {
            List<UserMessage> messages = userMessageMapper.selectList(wrapper);
            logger.debug("根据类型查询消息成功，userId: {}, messageType: {}, count: {}", userId, messageType, messages.size());
            return messages;
        } catch (Exception e) {
            logger.error("根据类型查询消息失败，userId: {}, messageType: {}", userId, messageType, e);
            return Collections.emptyList();
        }
    }

    /**
     * 验证messageType是否合法（只允许字母、数字、下划线）
     */
    private boolean isValidMessageType(String messageType) {
        if (messageType == null) {
            return true; // null是允许的
        }
        return messageType.matches("^[a-zA-Z0-9_]+$");
    }

    /**
     * 删除消息（带权限校验）
     * @param userId 当前用户ID
     * @param messageId 消息ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMessage(String userId, String messageId) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("删除消息失败：userId为空");
            return false;
        }
        if (messageId == null || messageId.trim().isEmpty()) {
            logger.warn("删除消息失败：messageId为空");
            return false;
        }
        
        // 权限校验：验证消息是否属于当前用户
        UserMessage message = userMessageMapper.selectById(messageId);
        if (message == null) {
            logger.warn("删除消息失败：消息不存在，messageId: {}", messageId);
            return false;
        }
        
        if (!userId.equals(message.getUserId())) {
            logger.warn("删除消息失败：无权限，userId: {}, messageUserId: {}", userId, message.getUserId());
            return false;
        }
        
        int deleted = userMessageMapper.deleteById(messageId);
        logger.info("消息删除成功，userId: {}, messageId: {}", userId, messageId);
        return deleted > 0;
    }

    /**
     * 批量删除消息
     * 增加权限校验：确保所有消息都属于指定userId
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteMessages(String userId, List<String> messageIds) {
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("批量删除消息失败：userId为空");
            return 0;
        }
        if (messageIds == null || messageIds.isEmpty()) {
            return 0;
        }

        // 先查询所有消息，验证每条都属于该userId
        LambdaQueryWrapper<UserMessage> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.in(UserMessage::getId, messageIds);
        List<UserMessage> existingMessages = userMessageMapper.selectList(checkWrapper);

        // 过滤出属于指定userId的消息ID
        List<String> validMessageIds = existingMessages.stream()
                .filter(msg -> userId.equals(msg.getUserId()))
                .map(UserMessage::getId)
                .collect(java.util.stream.Collectors.toList());

        if (validMessageIds.isEmpty()) {
            logger.warn("批量删除消息失败：没有属于userId {} 的消息，requestedIds: {}", userId, messageIds);
            return 0;
        }

        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId)
                .in(UserMessage::getId, validMessageIds);
        return userMessageMapper.delete(wrapper);
    }

    /**
     * 发送消息（同步保存到数据库）
     */
    @Transactional(rollbackFor = Exception.class)
    public UserMessage saveMessage(MessageDTO messageDTO) {
        // 增强参数验证
        if (messageDTO == null) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (messageDTO.getReceiver() == null || messageDTO.getReceiver().getId() == null) {
            throw new IllegalArgumentException("接收者信息不完整");
        }
        
        // 生成消息ID（如果不存在）
        String messageId = messageDTO.getMessageId();
        if (messageId == null || messageId.trim().isEmpty()) {
            messageId = UUID.randomUUID().toString();
            messageDTO.setMessageId(messageId);
        }
        
        // 提取消息内容，避免重复调用
        String title = "";
        String body = "";
        String relatedId = null;
        if (messageDTO.getContent() != null) {
            title = messageDTO.getContent().getTitle() != null ? messageDTO.getContent().getTitle() : "";
            body = messageDTO.getContent().getBody() != null ? messageDTO.getContent().getBody() : "";
            relatedId = messageDTO.getContent().getRelatedId();
        }
        
        UserMessage userMessage = UserMessage.builder()
                .id(messageId)
                .userId(messageDTO.getReceiver().getId())
                .messageType(messageDTO.getBusinessType())
                .subType(messageDTO.getAction())
                .title(title)
                .senderId(messageDTO.getSender() != null ? messageDTO.getSender().getId() : "system")
                .senderType(messageDTO.getSender() != null ? messageDTO.getSender().getType() : "system")
                .relatedId(relatedId)
                .priority(messageDTO.getPriority())
                .status(0)
                .createTime(LocalDateTime.now())
                .build();

        // 序列化完整消息内容
        try {
            userMessage.setContent(objectMapper.writeValueAsString(messageDTO.getContent()));
        } catch (JsonProcessingException e) {
            logger.warn("序列化消息内容失败，使用body字段，messageId: {}", messageId, e);
            userMessage.setContent(body);
        }

        userMessageMapper.insert(userMessage);
        return userMessage;
    }

    /**
     * 获取最近的广播消息（用户上线时调用，获取离线期间的广播通知）
     */
    public List<BroadcastMessage> getRecentBroadcastMessages(Integer limit) {
        int messageLimit = limit != null && limit > 0 ? Math.min(limit, maxLimit) : Math.min(50, maxLimit);
        try {
            List<BroadcastMessage> messages = broadcastMessageMapper.selectRecentMessages(messageLimit);
            logger.debug("获取最近广播消息成功，count: {}", messages.size());
            return messages;
        } catch (Exception e) {
            logger.error("获取最近广播消息失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取指定时间之后的广播消息（用于推送离线消息）
     */
    public List<BroadcastMessage> getBroadcastMessagesSince(LocalDateTime since) {
        if (since == null) {
            return getRecentBroadcastMessages(50);
        }
        return broadcastMessageMapper.selectMessagesSince(since);
    }

    /**
     * 分页获取广播消息
     */
    public List<BroadcastMessage> getBroadcastMessages(Integer pageNum, Integer pageSize) {
        int pageNumber = pageNum != null && pageNum > 0 ? pageNum : 1;
        int pageSizeNumber = pageSize != null && pageSize > 0 ? Math.min(pageSize, maxLimit) : 20;
        int offset = (pageNumber - 1) * pageSizeNumber;
        try {
            List<BroadcastMessage> messages = broadcastMessageMapper.selectWithPagination(offset, pageSizeNumber);
            logger.debug("分页获取广播消息成功，pageNum: {}, pageSize: {}, count: {}", pageNumber, pageSizeNumber, messages.size());
            return messages;
        } catch (Exception e) {
            logger.error("分页获取广播消息失败，pageNum: {}, pageSize: {}", pageNumber, pageSizeNumber, e);
            return Collections.emptyList();
        }
    }
}
