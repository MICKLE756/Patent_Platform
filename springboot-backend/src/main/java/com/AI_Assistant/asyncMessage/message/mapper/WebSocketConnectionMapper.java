package com.AI_Assistant.asyncMessage.message.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.AI_Assistant.asyncMessage.message.entity.WebSocketConnection;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * WebSocket连接状态Mapper接口
 */
@Mapper
public interface WebSocketConnectionMapper extends BaseMapper<WebSocketConnection> {

    /**
     * 根据用户ID查询连接记录
     */
    @Select("SELECT * FROM websocket_connection WHERE user_id = #{userId} AND status = 1")
    List<WebSocketConnection> selectByUserId(@Param("userId") String userId);

    /**
     * 根据会话ID查询连接记录
     */
    @Select("SELECT * FROM websocket_connection WHERE session_id = #{sessionId}")
    WebSocketConnection selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 更新心跳时间
     */
    @Update("UPDATE websocket_connection SET last_heartbeat = #{lastHeartbeat} WHERE id = #{id}")
    Integer updateHeartbeat(@Param("id") String id, @Param("lastHeartbeat") LocalDateTime lastHeartbeat);

    /**
     * 断开用户的所有连接
     */
    @Update("UPDATE websocket_connection SET status = 0 WHERE user_id = #{userId}")
    Integer disconnectByUserId(@Param("userId") String userId);

    /**
     * 根据会话ID断开连接
     */
    @Update("UPDATE websocket_connection SET status = 0 WHERE session_id = #{sessionId}")
    Integer disconnectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询超时连接（心跳时间超过5分钟）
     */
    @Select("SELECT * FROM websocket_connection WHERE status = 1 AND last_heartbeat < #{timeoutTime}")
    List<WebSocketConnection> selectTimeoutConnections(@Param("timeoutTime") LocalDateTime timeoutTime);
}