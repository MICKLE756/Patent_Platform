package com.AI_Assistant.asyncMessage.handler;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.AI_Assistant.asyncMessage.config.MessageProperties;
import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import com.AI_Assistant.asyncMessage.message.entity.BroadcastMessage;
import com.AI_Assistant.asyncMessage.message.entity.WebSocketConnection;
import com.AI_Assistant.asyncMessage.message.event.MessagePushListener;
import com.AI_Assistant.asyncMessage.message.mapper.WebSocketConnectionMapper;
import com.AI_Assistant.asyncMessage.message.service.MessageMonitorService;
import com.AI_Assistant.asyncMessage.message.service.MessageService;
import com.AI_Assistant.userCenter.auth.service.TokenService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * WebSocket消息处理器
 * 管理WebSocket连接和消息推送
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler implements MessagePushListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);

    /**
     * 存储在线用户的WebSocket会话（支持多设备登录）
     * key: userId, value: List<WebSocketSession>
     */
    private static final Map<String, List<WebSocketSession>> onlineUsers = new ConcurrentHashMap<>();

    /**
     * 存储会话的最后活跃时间
     * key: sessionId, value: lastActiveTime
     */
    private static final Map<String, LocalDateTime> sessionLastActiveTime = new ConcurrentHashMap<>();

    /**
     * 会话ID到用户ID的映射（用于快速查找超时会话所属用户）
     * key: sessionId, value: userId
     */
    private static final Map<String, String> sessionIdToUserId = new ConcurrentHashMap<>();

    /**
     * 会话ID到token的映射（用于定期token验证）
     * key: sessionId, value: token
     */
    private static final Map<String, String> sessionIdToToken = new ConcurrentHashMap<>();

    /**
     * 定时任务执行器，用于心跳检测和token验证
     * 使用配置化的线程池参数
     */
    private final ScheduledExecutorService heartbeatExecutor;

    /**
     * 广播消息线程池（用于异步广播，避免阻塞主线程）
     */
    private final ThreadPoolExecutor broadcastExecutor;

    private final WebSocketConnectionMapper connectionMapper;
    private final ObjectMapper objectMapper;
    private final MessageService messageService;
    private final MessageMonitorService monitorService;
    private final MessageProperties messageProperties;
    private final TokenService tokenService;
    private final long heartbeatTimeoutSeconds;
    private final long heartbeatCheckIntervalSeconds;
    private final long tokenValidationIntervalSeconds;
    private final int maxDevicesPerUser;
    private final int maxTotalConnections;

    /**
     * 构造器注入所有依赖
     */
    public WebSocketHandler(WebSocketConnectionMapper connectionMapper,
                           ObjectMapper objectMapper,
                           MessageService messageService,
                           MessageMonitorService monitorService,
                           MessageProperties messageProperties,
                           TokenService tokenService) {
        this.connectionMapper = connectionMapper;
        this.objectMapper = objectMapper;
        this.messageService = messageService;
        this.monitorService = monitorService;
        this.messageProperties = messageProperties;
        this.tokenService = tokenService;
        this.heartbeatTimeoutSeconds = messageProperties.getWebsocket().getHeartbeatTimeoutSeconds();
        this.heartbeatCheckIntervalSeconds = messageProperties.getWebsocket().getHeartbeatCheckIntervalSeconds();
        this.tokenValidationIntervalSeconds = messageProperties.getWebsocket().getTokenValidationIntervalSeconds();
        this.maxDevicesPerUser = messageProperties.getWebsocket().getMaxDevicesPerUser();
        this.maxTotalConnections = messageProperties.getWebsocket().getMaxTotalConnections();
        
        // 初始化心跳检测线程池
        this.heartbeatExecutor = new ScheduledThreadPoolExecutor(
                1,
                r -> {
                    Thread t = new Thread(r, "websocket-heartbeat-checker");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy() // 使用CallerRunsPolicy避免心跳任务被丢弃
        );
        
        // 初始化广播消息线程池（有界队列）
        this.broadcastExecutor = new ThreadPoolExecutor(
                2,    // corePoolSize
                4,    // maximumPoolSize
                60,   // keepAliveTime
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),  // 有界队列，避免内存溢出
                new ThreadFactory() {
                    private int counter = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "websocket-broadcast-" + counter++);
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 初始化心跳检测任务
     * 使用@PostConstruct确保依赖注入完成后再启动定时任务
     */
    @PostConstruct
    public void initHeartbeatTask() {
        // 注册线程池到监控服务
        if (heartbeatExecutor instanceof ThreadPoolExecutor) {
            monitorService.registerThreadPool("websocket-heartbeat", (ThreadPoolExecutor) heartbeatExecutor);
        }
        monitorService.registerThreadPool("websocket-broadcast", broadcastExecutor);
        
        heartbeatExecutor.scheduleAtFixedRate(this::checkHeartbeat, 
                heartbeatCheckIntervalSeconds, 
                heartbeatCheckIntervalSeconds, 
                TimeUnit.SECONDS);
        logger.info("WebSocket心跳检测任务已启动，超时时间: {}秒，检查间隔: {}秒", 
                heartbeatTimeoutSeconds, heartbeatCheckIntervalSeconds);
    }

    /**
     * 关闭线程池
     * 应用关闭时优雅地停止心跳检测任务和广播消息线程池
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭WebSocket心跳检测线程池...");
        shutdownExecutor(heartbeatExecutor, "心跳检测");
        
        logger.info("正在关闭WebSocket广播消息线程池...");
        shutdownExecutor(broadcastExecutor, "广播消息");
        
        logger.info("WebSocket所有线程池已关闭");
    }
    
    /**
     * 优雅关闭线程池
     */
    private void shutdownExecutor(java.util.concurrent.ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("WebSocket{}线程池关闭超时，强制关闭", name);
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.error("WebSocket{}线程池未能正常关闭", name);
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            logger.warn("WebSocket{}线程池关闭被中断", name);
        }
        logger.info("WebSocket{}线程池已关闭", name);
    }

    /**
     * 连接建立时调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserIdFromSession(session);
        
        // 验证用户ID，如果为空则关闭连接
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("WebSocket连接缺少用户信息，关闭连接，sessionId: {}", session.getId());
            session.close(CloseStatus.BAD_DATA.withReason("Missing userId"));
            return;
        }
        
        // 检查系统总连接数限制
        int totalConnections = getTotalConnectionCount();
        if (totalConnections >= maxTotalConnections) {
            logger.warn("WebSocket连接数已达上限，拒绝新连接，当前连接数: {}", totalConnections);
            session.close(CloseStatus.SERVER_ERROR.withReason("Server connection limit reached"));
            return;
        }
        
        // 检查单用户设备连接数限制
        List<WebSocketSession> userSessions = onlineUsers.get(userId);
        if (userSessions != null && userSessions.size() >= maxDevicesPerUser) {
            logger.warn("用户设备连接数已达上限，userId: {}, 当前设备数: {}", userId, userSessions.size());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Device limit exceeded"));
            return;
        }
        
        // 使用ConcurrentWebSocketSessionDecorator包装session，支持并发发送
        // 配置参数来自MessageProperties，避免硬编码
        int sendTimeoutMs = (int) messageProperties.getWebsocket().getSendTimeoutMs();
        int bufferSize = messageProperties.getWebsocket().getBufferSize();
        WebSocketSession decoratedSession = new ConcurrentWebSocketSessionDecorator(session, sendTimeoutMs, bufferSize);
        
        // 支持多设备登录，添加会话到列表
        onlineUsers.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(decoratedSession);
        
        // 记录会话最后活跃时间
        sessionLastActiveTime.put(session.getId(), LocalDateTime.now());
        
        // 记录sessionId到userId的映射（用于快速查找超时会话）
        sessionIdToUserId.put(session.getId(), userId);
        
        // 记录sessionId到token的映射（用于定期token验证）
        String token = getTokenFromSession(session);
        if (token != null && !token.isEmpty()) {
            sessionIdToToken.put(session.getId(), token);
        }
        
        // 记录连接状态到数据库
        saveConnectionToDatabase(userId, session);
        
        // 更新WebSocket连接计数
        monitorService.incrementWebSocketConnection();
        
        logger.info("WebSocket连接建立，userId: {}, sessionId: {}, 当前设备数: {}", 
                userId, session.getId(), 
                onlineUsers.get(userId) != null ? onlineUsers.get(userId).size() : 0);
        
        // 推送离线期间的广播消息（基于连接时间过滤，避免重复推送）
        pushOfflineBroadcastMessages(userId, session, LocalDateTime.now());
    }

    /**
     * 保存连接信息到数据库
     */
    private void saveConnectionToDatabase(String userId, WebSocketSession session) {
        try {
            WebSocketConnection connection = WebSocketConnection.builder()
                    .userId(userId)
                    .sessionId(session.getId())
                    .connectionTime(LocalDateTime.now())
                    .lastHeartbeat(LocalDateTime.now())
                    .status(1)
                    .build();
            connectionMapper.insert(connection);
        } catch (Exception e) {
            logger.error("保存WebSocket连接信息到数据库失败，userId: {}, sessionId: {}", userId, session.getId(), e);
            // 数据库保存失败不影响连接建立，记录日志即可
        }
    }

    /**
     * 推送离线期间的广播消息给指定用户
     * 用户上线时调用，确保离线期间的广播通知不遗漏
     * 通过连接时间过滤，避免重复推送
     */
    private void pushOfflineBroadcastMessages(String userId, WebSocketSession session, LocalDateTime connectTime) {
        try {
            // 获取用户连接时间之后的广播消息（最多50条）
            var recentBroadcasts = messageService.getBroadcastMessagesSince(connectTime.minusSeconds(5));
            
            if (recentBroadcasts != null && !recentBroadcasts.isEmpty()) {
                int pushedCount = 0;
                for (var broadcast : recentBroadcasts) {
                    if (pushBroadcastToSession(session, broadcast)) {
                        pushedCount++;
                    }
                }
                logger.info("已推送 {} 条离线广播消息给用户，userId: {}", pushedCount, userId);
            }
        } catch (Exception e) {
            logger.error("推送离线广播消息失败，userId: {}", userId, e);
        }
    }

    /**
     * 将单条广播消息推送到指定会话
     */
    private boolean pushBroadcastToSession(WebSocketSession session, BroadcastMessage broadcast) {
        try {
            MessageDTO messageDTO = objectMapper.readValue(broadcast.getContent(), MessageDTO.class);
            messageDTO.setMessageId(broadcast.getId());
            String jsonMessage = objectMapper.writeValueAsString(messageDTO);
            
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(jsonMessage));
                return true;
            }
        } catch (JsonProcessingException e) {
            logger.warn("解析广播消息失败，broadcastId: {}", broadcast.getId(), e);
        } catch (IOException e) {
            logger.error("推送广播消息失败，broadcastId: {}, sessionId: {}", broadcast.getId(), session.getId(), e);
        }
        return false;
    }

    /**
     * 接收到消息时调用
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("收到WebSocket消息: {}", payload);
        
        // 更新会话最后活跃时间
        sessionLastActiveTime.put(session.getId(), LocalDateTime.now());
        
        // 处理心跳消息
        if ("ping".equals(payload)) {
            handlePingMessage(session);
        }
    }

    /**
     * 处理心跳消息
     */
    private void handlePingMessage(WebSocketSession session) {
        try {
            session.sendMessage(new TextMessage("pong"));
            
            // 更新心跳时间到数据库
            String userId = getUserIdFromSession(session);
            if (userId != null) {
                WebSocketConnection connection = connectionMapper.selectBySessionId(session.getId());
                if (connection != null) {
                    connectionMapper.updateHeartbeat(connection.getId(), LocalDateTime.now());
                }
            }
        } catch (IOException e) {
            // 心跳发送失败属于正常网络波动，使用WARN级别
            logger.warn("发送心跳响应失败，sessionId: {}", session.getId(), e);
        }
    }

    /**
     * 处理传输错误
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        try {
            String userId = getUserIdFromSession(session);
            logger.error("WebSocket传输错误，userId: {}, sessionId: {}", userId, session.getId(), exception);

            // 移除失效的连接
            if (userId != null) {
                removeSession(userId, session.getId());
            }

            // 更新数据库连接状态
            disconnectSessionInDatabase(session.getId());
        } catch (Exception e) {
            // 捕获所有异常，避免传输错误处理导致WebSocket连接泄漏
            logger.error("处理WebSocket传输错误时发生异常，sessionId: {}", session.getId(), e);
        }
    }

    /**
     * 连接关闭时调用
     * 注意：先更新数据库，再清理内存，确保数据一致性
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        String sessionId = session.getId();
        
        if (userId != null) {
            // 1. 先更新数据库（保持数据一致性）
            disconnectSessionInDatabase(sessionId);
            
            // 2. 再清理内存（如果数据库更新失败，内存仍需清理以避免内存泄漏）
            removeSession(userId, sessionId);
            sessionLastActiveTime.remove(sessionId);
            
            // 清理sessionId到userId的映射
            sessionIdToUserId.remove(sessionId);
            
            // 清理sessionId到token的映射
            sessionIdToToken.remove(sessionId);
            
            // 更新WebSocket连接计数
            monitorService.decrementWebSocketConnection();
            
            logger.info("WebSocket连接关闭，userId: {}, sessionId: {}, 关闭状态: {}", userId, sessionId, status);
        }
    }

    /**
     * 在数据库中断开会话连接
     */
    private void disconnectSessionInDatabase(String sessionId) {
        try {
            connectionMapper.disconnectBySessionId(sessionId);
            logger.debug("WebSocket连接状态已更新到数据库，sessionId: {}", sessionId);
        } catch (Exception e) {
            logger.error("更新数据库连接状态失败，sessionId: {}, 错误: {}", sessionId, e.getMessage());
            // 数据库更新失败时，记录日志但继续清理内存
            // 数据库状态可以通过后台任务修复
        }
    }

    /**
     * 向指定用户推送消息（推送到用户的所有设备）
     *
     * @param userId 用户ID
     * @param message 消息内容
     * @return 是否推送成功（至少有一台设备成功）
     */
    public boolean pushMessage(String userId, MessageDTO message) {
        // 验证消息
        if (message == null) {
            logger.warn("推送消息为空，userId: {}", userId);
            return false;
        }
        
        if (userId == null || userId.trim().isEmpty()) {
            logger.warn("推送消息失败：userId为空");
            return false;
        }
        
        List<WebSocketSession> sessions = onlineUsers.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            logger.error("序列化消息失败，messageId: {}", message.getMessageId(), e);
            return false;
        }
        
        // 使用迭代器遍历，安全移除失效会话
        return pushToUserSessions(userId, sessions, jsonMessage);
    }

    /**
     * 向用户的所有会话推送消息
     * 使用迭代器遍历时使用synchronized保护sessions集合，避免并发修改异常
     */
    private boolean pushToUserSessions(String userId, List<WebSocketSession> sessions, String jsonMessage) {
        boolean pushed = false;
        // 先收集需要移除的会话，避免在迭代中多次修改集合
        List<WebSocketSession> toRemove = new java.util.ArrayList<>();

        // 在synchronized块内遍历，避免与removeSession的并发修改产生冲突
        synchronized (sessions) {
            for (WebSocketSession session : sessions) {
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(jsonMessage));
                        pushed = true;
                        // 更新活跃时间
                        sessionLastActiveTime.put(session.getId(), LocalDateTime.now());
                        logger.debug("消息已推送到用户设备，userId: {}, sessionId: {}", userId, session.getId());
                    } catch (IOException e) {
                        logger.error("推送消息到设备失败，userId: {}, sessionId: {}", userId, session.getId(), e);
                        toRemove.add(session);
                    }
                } else {
                    toRemove.add(session);
                }
            }

            // 统一移除失效会话
            if (!toRemove.isEmpty()) {
                sessions.removeAll(toRemove);
                for (WebSocketSession s : toRemove) {
                    sessionLastActiveTime.remove(s.getId());
                }
            }
        }

        // 如果用户没有在线会话了，从map中移除
        if (sessions.isEmpty()) {
            onlineUsers.remove(userId);
        }
        
        if (pushed) {
            logger.info("通过WebSocket推送消息成功，userId: {}, messageId: {}", userId, 
                    jsonMessage.contains("messageId") ? extractMessageId(jsonMessage) : "unknown");
        }
        
        return pushed;
    }

    /**
     * 从JSON中提取消息ID
     */
    private String extractMessageId(String jsonMessage) {
        try {
            int start = jsonMessage.indexOf("\"messageId\"");
            if (start > 0) {
                int valueStart = jsonMessage.indexOf(":", start) + 2;
                int valueEnd = jsonMessage.indexOf("\"", valueStart);
                return jsonMessage.substring(valueStart, valueEnd);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return "unknown";
    }

    /**
     * 检查用户是否在线
     */
    public boolean isOnline(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        List<WebSocketSession> sessions = onlineUsers.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        // 只要有一台设备在线就认为用户在线
        return sessions.stream().anyMatch(session -> session != null && session.isOpen());
    }

    /**
     * 获取在线用户数量
     */
    public int getOnlineUserCount() {
        return onlineUsers.size();
    }

    /**
     * 获取用户在线设备数量
     */
    public int getUserDeviceCount(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return 0;
        }
        List<WebSocketSession> sessions = onlineUsers.get(userId);
        if (sessions == null) {
            return 0;
        }
        return (int) sessions.stream().filter(session -> session != null && session.isOpen()).count();
    }

    /**
     * 获取系统总连接数
     */
    public int getTotalConnectionCount() {
        return onlineUsers.values().stream()
                .mapToInt(sessions -> (int) sessions.stream()
                        .filter(session -> session != null && session.isOpen())
                        .count())
                .sum();
    }

    /**
     * 向所有在线用户广播消息（异步方式）
     *
     * @param message 消息内容
     */
    public void broadcastMessageAsync(MessageDTO message) {
        // 验证消息
        if (message == null) {
            logger.warn("广播消息为空");
            return;
        }
        
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            logger.error("序列化广播消息失败", e);
            return;
        }

        // 使用广播线程池异步推送，避免阻塞主线程
        broadcastExecutor.submit(() -> broadcastToAllUsers(jsonMessage));
    }

    /**
     * 向所有在线用户广播消息（同步方式，保留用于兼容性）
     *
     * @param message 消息内容
     * @return 成功推送的用户数量
     */
    public int broadcastMessage(MessageDTO message) {
        // 验证消息
        if (message == null) {
            logger.warn("广播消息为空");
            return 0;
        }
        
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            logger.error("序列化广播消息失败", e);
            return 0;
        }

        return broadcastToAllUsers(jsonMessage);
    }

    /**
     * 向所有在线用户广播消息
     */
    private int broadcastToAllUsers(String jsonMessage) {
        int pushedCount = 0;
        List<String> emptyUserIds = new java.util.ArrayList<>();
        
        synchronized (onlineUsers) {
            for (java.util.Map.Entry<String, List<WebSocketSession>> entry : onlineUsers.entrySet()) {
                String userId = entry.getKey();
                List<WebSocketSession> sessions = entry.getValue();
                
                boolean userPushed = false;
                java.util.Iterator<WebSocketSession> sessionIterator = sessions.iterator();
                
                while (sessionIterator.hasNext()) {
                    WebSocketSession session = sessionIterator.next();
                    if (session != null && session.isOpen()) {
                        try {
                            session.sendMessage(new TextMessage(jsonMessage));
                            userPushed = true;
                            sessionLastActiveTime.put(session.getId(), LocalDateTime.now());
                        } catch (IOException e) {
                            logger.error("WebSocket广播推送失败，userId: {}, sessionId: {}", userId, session.getId(), e);
                            sessionIterator.remove();
                            sessionLastActiveTime.remove(session.getId());
                        }
                    } else {
                        sessionIterator.remove();
                        sessionLastActiveTime.remove(session.getId());
                    }
                }
                
                if (sessions.isEmpty()) {
                    emptyUserIds.add(userId);
                }
                
                if (userPushed) {
                    pushedCount++;
                }
            }
            
            for (String userId : emptyUserIds) {
                onlineUsers.remove(userId);
            }
        }
        
        logger.info("广播消息推送完成，成功推送给 {} 个用户", pushedCount);
        return pushedCount;
    }

    @Override
    public void pushBroadcastMessage(MessageDTO message) {
        // 使用异步方式推送广播消息，避免阻塞消费者线程
        broadcastMessageAsync(message);
    }

    /**
     * 根据用户ID和会话ID移除会话
     */
    private void removeSession(String userId, String sessionId) {
        List<WebSocketSession> sessions = onlineUsers.get(userId);
        if (sessions != null) {
            sessions.removeIf(session -> sessionId.equals(session.getId()));
            sessionLastActiveTime.remove(sessionId);
            // 清理sessionId到userId的映射
            sessionIdToUserId.remove(sessionId);
            if (sessions.isEmpty()) {
                onlineUsers.remove(userId);
            }
        }
    }

    /**
     * 根据用户ID移除所有会话（用于强制下线）
     */
    public void removeUser(String userId) {
        List<WebSocketSession> sessions = onlineUsers.get(userId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                sessionLastActiveTime.remove(session.getId());
                // 关闭会话
                closeSession(session, userId, session.getId());
            }
        }
        onlineUsers.remove(userId);
        try {
            connectionMapper.disconnectByUserId(userId);
        } catch (Exception e) {
            logger.error("更新数据库连接状态失败，userId: {}", userId, e);
        }
    }

    /**
     * 从会话中获取用户ID
     */
    private String getUserIdFromSession(WebSocketSession session) {
        // 这里需要根据实际的认证方式来获取用户ID
        // 可以从session的attributes中获取，或者从请求参数中解析
        Object userId = session.getAttributes().get("userId");
        return userId != null ? userId.toString() : null;
    }

    /**
     * 从会话中获取token
     */
    private String getTokenFromSession(WebSocketSession session) {
        // 从session的attributes中获取token
        Object token = session.getAttributes().get("token");
        return token != null ? token.toString() : null;
    }

    /**
     * 心跳检测任务
     * 检查所有会话的最后活跃时间，超时则关闭连接
     * 同时定期验证token有效性
     * 清理无效的映射关系，确保连接计数一致性
     */
    private void checkHeartbeat() {
        LocalDateTime now = LocalDateTime.now();
        
        // 遍历所有会话，检查超时和token有效性
        java.util.Iterator<Map.Entry<String, LocalDateTime>> iterator = 
                sessionLastActiveTime.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, LocalDateTime> entry = iterator.next();
            String sessionId = entry.getKey();
            LocalDateTime lastActive = entry.getValue();
            
            // 计算时间差
            long secondsSinceLastActive = Duration.between(lastActive, now).getSeconds();
            
            // 检查心跳超时
            if (secondsSinceLastActive > heartbeatTimeoutSeconds) {
                handleTimeoutSession(sessionId);
                iterator.remove();
                continue;
            }
            
            // 定期验证token（每tokenValidationIntervalSeconds秒验证一次）
            if (tokenValidationIntervalSeconds > 0 && secondsSinceLastActive % tokenValidationIntervalSeconds == 0) {
                validateTokenForSession(sessionId);
            }
        }
        
        // 清理无效的sessionId映射，确保连接计数一致性
        cleanUpInvalidMappings();
        
        // 定期检查线程池健康状态
        monitorService.checkThreadPoolHealth();
    }
    
    /**
     * 清理无效的映射关系
     * 确保sessionIdToUserId和sessionIdToToken与实际连接状态一致
     */
    private void cleanUpInvalidMappings() {
        // 清理无效的sessionId到userId映射
        sessionIdToUserId.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            String userId = entry.getValue();
            List<WebSocketSession> sessions = onlineUsers.get(userId);
            boolean isValid = sessions != null && sessions.stream()
                    .anyMatch(s -> sessionId.equals(s.getId()));
            if (!isValid) {
                logger.debug("清理无效的sessionId映射，sessionId: {}, userId: {}", sessionId, userId);
            }
            return !isValid;
        });
        
        // 清理无效的sessionId到token映射
        sessionIdToToken.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            boolean isValid = sessionLastActiveTime.containsKey(sessionId);
            if (!isValid) {
                logger.debug("清理无效的token映射，sessionId: {}", sessionId);
            }
            return !isValid;
        });
    }

    /**
     * 验证会话的token有效性
     * 如果token无效，则关闭连接
     */
    private void validateTokenForSession(String sessionId) {
        String token = sessionIdToToken.get(sessionId);
        if (token == null || token.isEmpty()) {
            logger.warn("会话缺少token，sessionId: {}", sessionId);
            return;
        }
        
        try {
            if (!tokenService.validateToken(token)) {
                logger.warn("WebSocket会话token已失效，关闭连接，sessionId: {}", sessionId);
                handleTokenExpiredSession(sessionId);
            }
        } catch (Exception e) {
            logger.error("验证token失败，sessionId: {}", sessionId, e);
        }
    }

    /**
     * 处理token过期的会话
     */
    private void handleTokenExpiredSession(String sessionId) {
        String userId = sessionIdToUserId.get(sessionId);
        
        if (userId == null) {
            logger.warn("token过期会话未找到对应的用户，sessionId: {}", sessionId);
            return;
        }
        
        // 根据用户ID查找会话
        WebSocketSession sessionToClose = null;
        List<WebSocketSession> sessions = onlineUsers.get(userId);
        if (sessions != null) {
            sessionToClose = sessions.stream()
                    .filter(session -> sessionId.equals(session.getId()))
                    .findFirst()
                    .orElse(null);
        }
        
        // 关闭会话
        if (sessionToClose != null) {
            try {
                sessionToClose.close(CloseStatus.SESSION_NOT_RELIABLE.withReason("Token expired"));
                logger.debug("WebSocket会话因token过期已关闭，userId: {}, sessionId: {}", userId, sessionId);
            } catch (IOException e) {
                logger.error("关闭token过期WebSocket会话失败，sessionId: {}", sessionId, e);
            }
        }
        
        // 清理相关映射
        removeSession(userId, sessionId);
        sessionIdToToken.remove(sessionId);
        
        // 更新数据库状态
        disconnectSessionInDatabase(sessionId);
    }

    /**
     * 处理超时会话（优化版：使用O(1)查找）
     */
    private void handleTimeoutSession(String sessionId) {
        logger.warn("WebSocket会话心跳超时，即将关闭连接，sessionId: {}", sessionId);
        
        // 使用sessionIdToUserId映射快速查找用户ID（O(1)复杂度）
        String userId = sessionIdToUserId.get(sessionId);
        
        if (userId == null) {
            logger.warn("超时会话未找到对应的用户，sessionId: {}", sessionId);
            return;
        }
        
        // 根据用户ID查找会话
        WebSocketSession sessionToClose = null;
        List<WebSocketSession> sessions = onlineUsers.get(userId);
        if (sessions != null) {
            sessionToClose = sessions.stream()
                    .filter(session -> sessionId.equals(session.getId()))
                    .findFirst()
                    .orElse(null);
        }
        
        // 关闭会话
        closeSession(sessionToClose, userId, sessionId);
        
        // 从onlineUsers中移除会话（清理内存）
        removeSession(userId, sessionId);
        
        // 更新数据库状态
        disconnectSessionInDatabase(sessionId);
    }

    /**
     * 关闭会话
     */
    private void closeSession(WebSocketSession session, String userId, String sessionId) {
        if (session != null) {
            try {
                session.close(CloseStatus.BAD_DATA.withReason("Session timeout"));
                logger.debug("WebSocket会话因心跳超时已关闭，userId: {}, sessionId: {}", userId, sessionId);
            } catch (IOException e) {
                logger.error("关闭超时WebSocket会话失败，sessionId: {}", sessionId, e);
            }
        }
    }
}