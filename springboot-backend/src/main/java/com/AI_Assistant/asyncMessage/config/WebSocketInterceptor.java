package com.AI_Assistant.asyncMessage.config;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.AI_Assistant.userCenter.auth.service.TokenService;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * WebSocket握手拦截器
 * 
 * 在WebSocket连接建立前进行验证（如Token验证）
 */
public class WebSocketInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketInterceptor.class);

    private final TokenService tokenService;

    public WebSocketInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        // 获取请求中的Token参数
        String query = request.getURI().getQuery();
        if (query != null && !query.isEmpty()) {
            String token = extractToken(query);
            if (token != null && !token.trim().isEmpty()) {
                try {
                    // 先使用TokenService.validateToken验证Token是否在Redis中有效（未过期）
                    if (!tokenService.validateToken(token)) {
                        logger.warn("WebSocket握手失败：Token已过期或无效");
                        return false;
                    }
                    // 验证token并获取用户信息
                    User user = tokenService.getUserFromToken(token);
                    if (user != null) {
                        // 将用户ID存入attributes供后续使用
                        attributes.put("userId", user.getId());
                        attributes.put("user", user);
                        logger.info("WebSocket握手成功，userId: {}", user.getId());
                        return true;
                    } else {
                        logger.warn("WebSocket握手失败：Token无效");
                        return false;
                    }
                } catch (Exception e) {
                    logger.warn("WebSocket握手失败：Token验证异常 - {}", e.getMessage());
                    return false;
                }
            }
        }

        logger.warn("WebSocket握手失败：缺少Token参数或Token为空");
        return false;
    }

    /**
     * 检查Token是否过期
     * 已废弃：使用tokenService.validateToken替代
     */
    @Deprecated
    private boolean isTokenExpired(String token) {
        try {
            return !tokenService.validateToken(token);
        } catch (Exception e) {
            logger.warn("检查Token过期状态失败", e);
            return true;
        }
    }

    /**
     * 从查询字符串中提取Token（更健壮的解析）
     */
    private String extractToken(String query) {
        // 按&分割查询参数
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && "token".equalsIgnoreCase(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 握手完成后的处理
    }
}