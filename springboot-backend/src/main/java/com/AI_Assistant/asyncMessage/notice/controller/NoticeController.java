package com.AI_Assistant.asyncMessage.notice.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.dto.UnreadCountDTO;
import com.AI_Assistant.asyncMessage.message.entity.BroadcastMessage;
import com.AI_Assistant.asyncMessage.message.entity.UserMessage;
import com.AI_Assistant.asyncMessage.message.service.MessageProducerService;
import com.AI_Assistant.asyncMessage.message.service.MessageService;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.exception.BadRequestException;
import com.AI_Assistant.common.exception.ForbiddenException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.exception.UnauthorizedException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * 通知控制器
 * 处理系统通知相关的REST API
 */
@RestController
@RequestMapping("/api/v1")
public class NoticeController {

    private static final Logger logger = LoggerFactory.getLogger(NoticeController.class);

    private final MessageService messageService;
    private final MessageProducerService messageProducerService;

    /**
     * 构造器注入依赖
     */
    public NoticeController(MessageService messageService, MessageProducerService messageProducerService) {
        this.messageService = messageService;
        this.messageProducerService = messageProducerService;
    }

    /**
     * 获取用户通知列表
     */
    @GetMapping("/notices")
    public Result<List<UserMessage>> getNotices(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "20") Integer pageSize) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        List<UserMessage> notices = messageService.getMessagesByUserId(currentUser.getId(), pageNum, pageSize);
        return Result.ok("获取通知列表成功", notices);
    }

    /**
     * 获取用户消息列表（包含所有类型）
     */
    @GetMapping("/messages/notices")
    public Result<List<UserMessage>> getUserMessages() {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        List<UserMessage> messages = messageService.getMessagesByUserId(currentUser.getId(), 1, 20);
        return Result.ok("获取消息列表成功", messages);
    }

    /**
     * 发布系统通知（管理员）
     */
    @PostMapping("/notices/system")
    public Result<String> createSystemNotice(@RequestBody NoticeRequest request) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        if (currentUser.getUserType() == null || currentUser.getUserType() != 3) {
            throw new ForbiddenException("只有管理员可以发布系统通知");
        }

        if (request == null || request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new BadRequestException("通知标题不能为空");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new BadRequestException("通知内容不能为空");
        }
        
        if (request.getContent() != null && request.getContent().length() > 2000) {
            throw new BadRequestException("通知内容过长，最多2000字符");
        }

        MessageDTO message = MessageDTO.builder()
                .businessType("notice")
                .action("publish")
                .sender(MessageDTO.Sender.builder()
                        .id(currentUser.getId())
                        .type("admin")
                        .name(currentUser.getUsername())
                        .build())
                // 广播消息的receiver设置为null，与MessageConsumerService中的广播判断逻辑保持一致
                .receiver(null)
                .content(MessageDTO.Content.builder()
                        .title(request.getTitle().trim())
                        .body(request.getContent().trim())
                        .build())
                .priority(2)
                .build();

        // 发送广播消息（通过Fanout交换机）
        messageProducerService.sendBroadcastMessage(message);

        logger.info("管理员发布系统通知，adminId: {}, title: {}", currentUser.getId(), request.getTitle());
        return Result.ok("发布系统通知成功", "发布系统通知成功");
    }

    /**
     * 获取未读消息数量
     */
    @GetMapping("/messages/unread/count")
    public Result<UnreadCountDTO> getUnreadCount() {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        UnreadCountDTO countDTO = messageService.getUnreadCount(currentUser.getId());
        return Result.ok("获取未读消息数量成功", countDTO);
    }

    /**
     * 获取未读消息列表（登录时拉取离线消息）
     */
    @GetMapping("/messages/unread")
    public Result<List<UserMessage>> getUnreadMessages() {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        List<UserMessage> messages = messageService.getUnreadMessages(currentUser.getId());
        return Result.ok("获取未读消息成功", messages);
    }

    /**
     * 标记消息为已读
     */
    @PostMapping("/messages/{messageId}/read")
    public Result<String> markAsRead(@PathVariable String messageId) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        UserMessage message = messageService.getMessageById(messageId);
        if (message == null) {
            throw new NotFoundException("消息不存在");
        }

        if (!currentUser.getId().equals(message.getUserId())) {
            throw new ForbiddenException("无权操作该消息");
        }

        boolean success = messageService.markAsRead(messageId);
        if (success) {
            return Result.ok("标记已读成功", "标记已读成功");
        } else {
            throw new BadRequestException("标记已读失败");
        }
    }

    /**
     * 标记所有消息为已读
     */
    @PostMapping("/messages/read/all")
    public Result<String> markAllAsRead() {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        int count = messageService.markAllAsRead(currentUser.getId());
        return Result.ok("已标记 " + count + " 条消息为已读", "已标记 " + count + " 条消息为已读");
    }

    /**
     * 删除消息
     */
    @DeleteMapping("/messages/{messageId}")
    public Result<String> deleteMessage(@PathVariable String messageId) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        UserMessage message = messageService.getMessageById(messageId);
        if (message == null) {
            throw new NotFoundException("消息不存在");
        }

        if (!currentUser.getId().equals(message.getUserId())) {
            throw new ForbiddenException("无权操作该消息");
        }

        boolean success = messageService.deleteMessage(currentUser.getId(), messageId);
        if (success) {
            return Result.ok("删除成功", "删除成功");
        } else {
            throw new BadRequestException("删除失败");
        }
    }

    /**
     * 获取广播消息列表（系统通知）
     * 用户上线时调用，获取离线期间的广播通知
     */
    @GetMapping("/notices/broadcast")
    public Result<List<BroadcastMessage>> getBroadcastMessages(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            throw new UnauthorizedException("用户未登录");
        }

        List<BroadcastMessage> messages;
        if (pageNum != null && pageSize != null) {
            messages = messageService.getBroadcastMessages(pageNum, pageSize);
        } else {
            // 默认获取最近的50条广播消息
            messages = messageService.getRecentBroadcastMessages(50);
        }
        return Result.ok("获取广播消息成功", messages);
    }

    /**
     * 通知请求DTO
     */
    public static class NoticeRequest {
        private String title;
        private String content;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}