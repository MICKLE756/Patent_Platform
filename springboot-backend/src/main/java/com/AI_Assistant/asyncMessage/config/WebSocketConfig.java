package com.AI_Assistant.asyncMessage.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.AI_Assistant.asyncMessage.handler.WebSocketHandler;
import com.AI_Assistant.userCenter.auth.service.TokenService;

/**
 * WebSocket配置类
 * 
 * 配置WebSocket端点和拦截器
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final TokenService tokenService;
    private final MessageProperties messageProperties;

    public WebSocketConfig(WebSocketHandler webSocketHandler, TokenService tokenService,
                          MessageProperties messageProperties) {
        this.webSocketHandler = webSocketHandler;
        this.tokenService = tokenService;
        this.messageProperties = messageProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 获取允许的来源白名单，配置在MessageProperties中
        List<String> allowedOrigins = messageProperties.getWebsocket().getAllowedOrigins();
        
        registry.addHandler(webSocketHandler, "/ws/messages")
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]))
                .addInterceptors(new WebSocketInterceptor(tokenService));
    }
}