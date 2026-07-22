package com.AI_Assistant.asyncMessage.message.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.asyncMessage.intentionMessage.entity.IntentionMessage;
import com.AI_Assistant.asyncMessage.intentionMessage.mapper.IntentionMessageMapper;
import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.service.MessageProducerService;
import com.AI_Assistant.asyncMessage.util.PermissionCheckUtils;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.user.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 消息控制器
 * 处理意向留言相关的REST API
 */
@RestController
@RequestMapping("/api/v1")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    private final IntentionMessageMapper intentionMessageMapper;
    private final MessageProducerService messageProducerService;

    /**
     * 构造器注入依赖
     */
    public MessageController(IntentionMessageMapper intentionMessageMapper,
                           MessageProducerService messageProducerService) {
        this.intentionMessageMapper = intentionMessageMapper;
        this.messageProducerService = messageProducerService;
    }

    /**
     * 发起意向留言（企业用户→科研团队）
     */
    @PostMapping("/messages/intention")
    public Result<String> createIntention(@RequestBody IntentionRequest request) {
        // 使用公共权限校验工具类
        PermissionCheckUtils.checkEnterpriseUser(UserContext.getUser());
        
        User currentUser = UserContext.getUser();

        // 验证必填参数
        if (request == null) {
            return Result.<String>badRequest("请求参数不能为空", null);
        }
        if (request.getReceiverId() == null || request.getReceiverId().trim().isEmpty()) {
            return Result.<String>badRequest("接收者ID不能为空", null);
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            return Result.<String>badRequest("留言内容不能为空", null);
        }
        if (request.getContent() != null && request.getContent().length() > 1000) {
            return Result.<String>badRequest("留言内容过长，最多1000字符", null);
        }

        // 创建意向留言记录
        IntentionMessage intention = IntentionMessage.builder()
                .initiatorId(currentUser.getId())
                .initiatorType("enterprise")
                .receiverId(request.getReceiverId().trim())
                .receiverType("research_team")
                .content(request.getContent().trim())
                .companyAddress(request.getCompanyAddress() != null ? request.getCompanyAddress().trim() : null)
                .companyAttr(request.getCompanyAttr() != null ? request.getCompanyAttr().trim() : null)
                .adminStatus("pending")
                .teamStatus("pending")
                .readStatus(0)
                .status(1)
                .build();

        intentionMessageMapper.insert(intention);

        // 发送消息通知管理员审核
        MessageDTO message = MessageDTO.builder()
                .businessType("intention")
                .action("create")
                .sender(MessageDTO.Sender.builder()
                        .id(currentUser.getId())
                        .type("enterprise")
                        .name(currentUser.getUsername())
                        .build())
                .receiver(MessageDTO.Receiver.builder()
                        .id(request.getReceiverId())
                        .type("research_team")
                        .build())
                .content(MessageDTO.Content.builder()
                        .title("新的意向留言")
                        .body(request.getContent())
                        .relatedId(intention.getId())
                        .relatedType("intention")
                        .build())
                .priority(1)
                .build();

        // 发送消息到意向留言队列
        messageProducerService.sendIntentionMessage(message);

        logger.info("企业用户发起意向留言，enterpriseId: {}, receiverId: {}", currentUser.getId(), request.getReceiverId());
        return Result.ok("发起意向留言成功", "发起意向留言成功");
    }

    /**
     * 获取待审核消息列表（管理员）
     */
    @GetMapping("/messages/pending")
    public Result<List<IntentionMessage>> getPendingMessages() {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            return Result.<List<IntentionMessage>>unauthorized("用户未登录", null);
        }

        // 验证用户类型（必须是管理员）
        if (currentUser.getUserType() == null || currentUser.getUserType() != 3) {
            return Result.<List<IntentionMessage>>forbidden("只有管理员可以查看待审核消息", null);
        }

        LambdaQueryWrapper<IntentionMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentionMessage::getAdminStatus, "pending")
                .orderByDesc(IntentionMessage::getSendTime);

        List<IntentionMessage> messages = intentionMessageMapper.selectList(wrapper);
        return Result.ok("获取待审核消息成功", messages);
    }

    /**
     * 获取消息审核详情
     */
    @GetMapping("/messages/{messageId}/audit")
    public Result<IntentionMessage> getMessageAudit(@PathVariable String messageId) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            return Result.<IntentionMessage>unauthorized("用户未登录", null);
        }

        IntentionMessage message = intentionMessageMapper.selectById(messageId);
        if (message == null) {
            return Result.<IntentionMessage>notFound("消息不存在", null);
        }

        // 权限校验：只有消息的发起者、接收者或管理员可以查看
        boolean isInitiator = currentUser.getId().equals(message.getInitiatorId());
        boolean isReceiver = currentUser.getId().equals(message.getReceiverId());
        boolean isAdmin = currentUser.getUserType() != null && currentUser.getUserType() == 3;

        if (!isInitiator && !isReceiver && !isAdmin) {
            return Result.<IntentionMessage>forbidden("无权查看该消息", null);
        }

        return Result.ok("获取消息详情成功", message);
    }

    /**
     * 审核消息（管理员）
     */
    @PostMapping("/messages/{messageId}/audit")
    public Result<String> auditMessage(@PathVariable String messageId, @RequestBody AuditRequest request) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            return Result.<String>unauthorized("用户未登录", null);
        }

        // 验证用户类型（必须是管理员）
        if (currentUser.getUserType() == null || currentUser.getUserType() != 3) {
            return Result.<String>forbidden("只有管理员可以审核消息", null);
        }
        
        // 验证请求参数
        if (request == null || request.getResult() == null || request.getResult().trim().isEmpty()) {
            return Result.<String>badRequest("审核结果不能为空", null);
        }
        if (!"approved".equals(request.getResult()) && !"rejected".equals(request.getResult())) {
            return Result.<String>badRequest("无效的审核结果，只能是approved或rejected", null);
        }
        if (request.getRemark() != null && request.getRemark().length() > 500) {
            return Result.<String>badRequest("审核意见过长，最多500字符", null);
        }

        IntentionMessage message = intentionMessageMapper.selectById(messageId);
        if (message == null) {
            return Result.<String>notFound("消息不存在", null);
        }

        // 更新审核状态
        if ("approved".equals(request.getResult())) {
            message.setAdminStatus("approved");
        } else if ("rejected".equals(request.getResult())) {
            message.setAdminStatus("rejected");
        }
        
        // 保存审核意见
        if (request.getRemark() != null) {
            message.setAdminRemark(request.getRemark().trim());
        }

        message.setAdminId(currentUser.getId());
        message.setAdminAuditTime(new java.util.Date());
        intentionMessageMapper.updateById(message);

        // 发送审核结果通知
        sendAuditResultMessage(message, request.getResult());

        logger.info("管理员审核意向留言，messageId: {}, result: {}", messageId, request.getResult());
        return Result.ok("处理审核成功", "处理审核成功");
    }

    /**
     * 科研团队审核意向留言
     */
    @PostMapping("/messages/{messageId}/team-audit")
    public Result<String> teamAuditMessage(@PathVariable String messageId, @RequestBody AuditRequest request) {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            return Result.<String>unauthorized("用户未登录", null);
        }

        // 验证用户类型（必须是科研团队用户）
        if (currentUser.getUserType() == null || currentUser.getUserType() != 2) {
            return Result.<String>forbidden("只有科研团队用户可以审核", null);
        }
        
        // 验证请求参数
        if (request == null || request.getResult() == null || request.getResult().trim().isEmpty()) {
            return Result.<String>badRequest("审核结果不能为空", null);
        }
        if (!"approved".equals(request.getResult()) && !"rejected".equals(request.getResult())) {
            return Result.<String>badRequest("无效的审核结果，只能是approved或rejected", null);
        }
        if (request.getRemark() != null && request.getRemark().length() > 500) {
            return Result.<String>badRequest("审核意见过长，最多500字符", null);
        }

        IntentionMessage message = intentionMessageMapper.selectById(messageId);
        if (message == null) {
            return Result.<String>notFound("消息不存在", null);
        }

        // 验证消息接收者
        if (!currentUser.getId().equals(message.getReceiverId())) {
            return Result.<String>forbidden("无权审核此消息", null);
        }

        // 更新审核状态
        if ("approved".equals(request.getResult())) {
            message.setTeamStatus("approved");
        } else if ("rejected".equals(request.getResult())) {
            message.setTeamStatus("rejected");
        }
        
        // 保存审核意见
        if (request.getRemark() != null) {
            message.setTeamRemark(request.getRemark().trim());
        }

        message.setTeamAuditTime(new java.util.Date());
        intentionMessageMapper.updateById(message);

        // 发送审核结果通知给企业用户
        sendTeamAuditResultMessage(message, request.getResult());

        logger.info("科研团队审核意向留言，messageId: {}, result: {}", messageId, request.getResult());
        return Result.ok("处理审核成功", "处理审核成功");
    }

    /**
     * 发送审核结果消息
     */
    private void sendAuditResultMessage(IntentionMessage intention, String result) {
        String title = "意向留言审核" + ("approved".equals(result) ? "通过" : "拒绝");
        MessageDTO message = MessageDTO.builder()
                .businessType("audit")
                .action(result)
                .sender(MessageDTO.Sender.builder()
                        .id("system")
                        .type("system")
                        .name("系统")
                        .build())
                .receiver(MessageDTO.Receiver.builder()
                        .id(intention.getInitiatorId())
                        .type(intention.getInitiatorType())
                        .build())
                .content(MessageDTO.Content.builder()
                        .title(title)
                        .body("您的意向留言已被" + ("approved".equals(result) ? "通过" : "拒绝"))
                        .relatedId(intention.getId())
                        .relatedType("intention")
                        .build())
                .priority(1)
                .build();

        messageProducerService.sendAuditMessage(message);
    }

    /**
     * 发送科研团队审核结果消息
     */
    private void sendTeamAuditResultMessage(IntentionMessage intention, String result) {
        String title = "科研团队审核" + ("approved".equals(result) ? "通过" : "拒绝");
        MessageDTO message = MessageDTO.builder()
                .businessType("intention")
                .action(result)
                .sender(MessageDTO.Sender.builder()
                        .id(intention.getReceiverId())
                        .type("research_team")
                        .name("科研团队")
                        .build())
                .receiver(MessageDTO.Receiver.builder()
                        .id(intention.getInitiatorId())
                        .type(intention.getInitiatorType())
                        .build())
                .content(MessageDTO.Content.builder()
                        .title(title)
                        .body("您的意向留言已被科研团队" + ("approved".equals(result) ? "接受" : "拒绝"))
                        .relatedId(intention.getId())
                        .relatedType("intention")
                        .build())
                .priority(1)
                .build();

        messageProducerService.sendIntentionMessage(message);
    }

    /**
     * 意向留言请求DTO
     */
    public static class IntentionRequest {
        private String receiverId;
        private String content;
        private String companyAddress;
        private String companyAttr;

        public String getReceiverId() {
            return receiverId;
        }

        public void setReceiverId(String receiverId) {
            this.receiverId = receiverId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getCompanyAddress() {
            return companyAddress;
        }

        public void setCompanyAddress(String companyAddress) {
            this.companyAddress = companyAddress;
        }

        public String getCompanyAttr() {
            return companyAttr;
        }

        public void setCompanyAttr(String companyAttr) {
            this.companyAttr = companyAttr;
        }
    }

    /**
     * 审核请求DTO
     */
    public static class AuditRequest {
        private String result; // approved/rejected
        private String remark; // 审核意见

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }
}