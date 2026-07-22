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
 * WebSocket连接状态实体类
 * 映射websocket_connection表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("websocket_connection")
public class WebSocketConnection {

    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("connection_time")
    private LocalDateTime connectionTime;

    @TableField("last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @TableField("status")
    private Integer status;
}