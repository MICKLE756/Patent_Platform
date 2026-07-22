package com.AI_Assistant.asyncMessage.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户消息实体类
 * 映射user_message表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_message")
public class UserMessage {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("message_type")
    private String messageType;

    @TableField("sub_type")
    private String subType;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("sender_id")
    private String senderId;

    @TableField("sender_type")
    private String senderType;

    @TableField("related_id")
    private String relatedId;

    @TableField("priority")
    private Integer priority;

    @TableField("status")
    private Integer status;

    @TableField("create_time")
    private LocalDateTime createTime;
}