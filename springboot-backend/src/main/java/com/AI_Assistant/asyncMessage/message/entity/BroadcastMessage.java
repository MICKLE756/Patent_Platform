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
 * 广播消息实体类
 * 存储系统广播通知，独立于用户消息表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("broadcast_message")
public class BroadcastMessage {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("sender_id")
    private String senderId;

    @TableField("sender_type")
    private String senderType;

    @TableField("priority")
    private Integer priority;

    @TableField("create_time")
    private LocalDateTime createTime;
}
