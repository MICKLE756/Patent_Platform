package com.AI_Assistant.backend.admin.controller;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.asyncMessage.intentionMessage.entity.IntentionMessage;
import com.AI_Assistant.asyncMessage.intentionMessage.mapper.IntentionMessageMapper;
import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.service.MessageProducerService;
import com.AI_Assistant.backend.admin.dto.RejectIntentionRequest;
import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.exception.ConflictException;
import com.AI_Assistant.common.exception.NotFoundException;
import com.AI_Assistant.common.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 管理员审核控制器
 * 提供管理员专属的审核操作接口
 * 
 * 注意：此类需要权限验证，普通用户无法访问
 * 所需权限：permission_intention_audit = 1
 * 权限验证由 PermissionInterceptor 统一处理
 */
@RestController
@RequestMapping("/api/v1/admin/approvals")
public class AdminApprovalController {

    private static final Logger logger = LoggerFactory.getLogger(AdminApprovalController.class);

    @Autowired
    private IntentionMessageMapper intentionMessageMapper;

    @Autowired
    private MessageProducerService messageProducerService;

    /**
     * 获取待审核的意向留言列表（分页）
     * GET /api/v1/admin/approvals/pending
     * 
     * @param pageNum 页码，默认1
     * @param pageSize 每页大小，默认10
     * @return 分页结果
     */
    @GetMapping("/pending")
    public Result<IPage<IntentionMessage>> getPendingApprovals(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        logger.info("管理员查询待审核意向留言列表，pageNum: {}, pageSize: {}", pageNum, pageSize);

        LambdaQueryWrapper<IntentionMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentionMessage::getAdminStatus, "pending")
               .eq(IntentionMessage::getStatus, 1)
               .orderByDesc(IntentionMessage::getSendTime);

        Page<IntentionMessage> page = new Page<>(pageNum, pageSize);
        IPage<IntentionMessage> result = intentionMessageMapper.selectPage(page, wrapper);

        logger.info("查询到待审核意向留言数量: {}", result.getTotal());
        return Result.ok(result);
    }

    /**
     * 获取单个审核详情
     * GET /api/v1/admin/approvals/{intentionId}
     */
    @GetMapping("/{intentionId}")
    public Result<IntentionMessage> getApprovalDetail(@PathVariable String intentionId) {
        logger.info("管理员查询审核详情，intentionId: {}", intentionId);

        IntentionMessage message = intentionMessageMapper.selectById(intentionId);
        if (message == null) {
            logger.warn("意向留言不存在，intentionId: {}", intentionId);
            throw new NotFoundException("意向留言不存在");
        }

        return Result.ok(message);
    }

    /**
     * 审核通过意向留言
     * PUT /api/v1/admin/approvals/{intentionId}/approve
     * 
     * 审核通过后：
     * - admin_status 变为 approved
     * - admin_id 记录操作管理员ID
     * - admin_audit_time 记录审核时间
     */
    @PutMapping("/{intentionId}/approve")
    public Result<Void> approveIntention(@PathVariable String intentionId) {
        logger.info("管理员审核通过意向留言，intentionId: {}", intentionId);

        IntentionMessage message = intentionMessageMapper.selectById(intentionId);
        if (message == null) {
            logger.warn("意向留言不存在，intentionId: {}", intentionId);
            throw new NotFoundException("意向留言不存在");
        }

        if (!"pending".equals(message.getAdminStatus())) {
            logger.warn("该意向留言状态不是待审核，intentionId: {}, status: {}", intentionId, message.getAdminStatus());
            throw new ConflictException("该意向留言状态不是待审核");
        }

        message.setAdminStatus("approved");
        message.setAdminId(UserContext.getUserId());
        message.setAdminAuditTime(new Date());
        intentionMessageMapper.updateById(message);

        sendApprovalNotification(message, "approved", null);

        logger.info("意向留言审核通过成功，intentionId: {}", intentionId);
        return Result.ok("审核通过成功");
    }

    /**
     * 拒绝意向留言
     * PUT /api/v1/admin/approvals/{intentionId}/reject
     * 
     * 请求体: { "rejectionReason": "拒绝理由" }
     * 
     * 拒绝后：
     * - admin_status 变为 rejected
     * - admin_id 记录操作管理员ID
     * - admin_audit_time 记录审核时间
     * - 拒绝理由记录到回绝日志表（后续实现）
     */
    @PutMapping("/{intentionId}/reject")
    // [已修复 - 问题61] 添加@Validated进行参数校验
    public Result<Void> rejectIntention(
            @PathVariable String intentionId,
            @RequestBody @Validated RejectIntentionRequest request) {
        
        String rejectionReason = request.getRejectionReason();
        logger.info("管理员拒绝意向留言，intentionId: {}, reason: {}", intentionId, rejectionReason);

        IntentionMessage message = intentionMessageMapper.selectById(intentionId);
        if (message == null) {
            logger.warn("意向留言不存在，intentionId: {}", intentionId);
            throw new NotFoundException("意向留言不存在");
        }

        if (!"pending".equals(message.getAdminStatus())) {
            logger.warn("该意向留言状态不是待审核，intentionId: {}, status: {}", intentionId, message.getAdminStatus());
            throw new ConflictException("该意向留言状态不是待审核");
        }

        message.setAdminStatus("rejected");
        message.setAdminId(UserContext.getUserId());
        message.setAdminAuditTime(new Date());
        intentionMessageMapper.updateById(message);

        // TODO: 记录回绝日志到 rejection_log 表

        sendApprovalNotification(message, "rejected", rejectionReason);

        logger.info("意向留言拒绝成功，intentionId: {}", intentionId);
        return Result.ok("拒绝成功");
    }

    /**
     * 发送审核通知消息
     *
     * @param message      意向留言实体
     * @param approvalType 审核类型：approved/rejected
     * @param reason       拒绝理由（审核通过时为null）
     */
    private void sendApprovalNotification(IntentionMessage message, String approvalType, String reason) {
        try {
            String initiatorId = message.getInitiatorId();
            String initiatorType = message.getInitiatorType();

            MessageDTO notification = MessageDTO.builder()
                    .businessType("audit")
                    .action(approvalType)
                    .sender(MessageDTO.Sender.builder()
                            .id(UserContext.getUserId())
                            .type("admin")
                            .name("管理员")
                            .build())
                    .receiver(MessageDTO.Receiver.builder()
                            .id(initiatorId)
                            .type(initiatorType)
                            .build())
                    .content(MessageDTO.Content.builder()
                            .title("意向留言审核结果")
                            .body("approved".equals(approvalType) 
                                    ? "您的意向留言已通过审核" 
                                    : "您的意向留言未通过审核，原因：" + reason)
                            .relatedId(message.getId())
                            .relatedType("intention")
                            .build())
                    .priority(2)
                    .timestamp(System.currentTimeMillis())
                    .build();

            messageProducerService.sendNoticeMessageAsync(notification);
            logger.info("审核通知已发送，intentionId: {}, approvalType: {}", message.getId(), approvalType);
        } catch (Exception e) {
            logger.error("发送审核通知失败，intentionId: {}", message.getId(), e);
        }
    }

    /**
     * 获取审核历史记录（分页）
     * GET /api/v1/admin/approvals/history
     * 
     * @param status 可选筛选状态：approved/rejected
     * @return 分页结果
     */
    @GetMapping("/history")
    public Result<IPage<IntentionMessage>> getApprovalHistory(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        
        logger.info("管理员查询审核历史，pageNum: {}, pageSize: {}, status: {}", pageNum, pageSize, status);

        LambdaQueryWrapper<IntentionMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(IntentionMessage::getStatus, 1);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(IntentionMessage::getAdminStatus, status);
        } else {
            wrapper.ne(IntentionMessage::getAdminStatus, "pending");
        }

        wrapper.orderByDesc(IntentionMessage::getAdminAuditTime);

        Page<IntentionMessage> page = new Page<>(pageNum, pageSize);
        IPage<IntentionMessage> result = intentionMessageMapper.selectPage(page, wrapper);

        logger.info("查询到审核历史数量: {}", result.getTotal());
        return Result.ok(result);
    }
}