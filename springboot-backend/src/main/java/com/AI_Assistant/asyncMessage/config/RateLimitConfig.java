package com.AI_Assistant.asyncMessage.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息限流配置
 * 限制消息消费速率，防止消息风暴
 */
@Configuration
public class RateLimitConfig {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

    /**
     * 消息配置属性
     */
    private final MessageProperties messageProperties;

    /**
     * 构造器注入配置属性
     */
    public RateLimitConfig(MessageProperties messageProperties) {
        this.messageProperties = messageProperties;
    }

    /**
     * 获取每秒最大消息处理数
     */
    public int getMaxMessagesPerSecond() {
        return messageProperties.getRateLimit().getMaxMessagesPerSecond();
    }

    /**
     * 创建消息计数器（用于限流）
     * key: 消息类型，value: 计数器
     * [已废弃 - 问题23] 已移至RateLimitService内部初始化，不再作为Bean注入
     */
    // @Bean
    // public ConcurrentHashMap<String, AtomicInteger> messageRateCounter() {
    //     return new ConcurrentHashMap<>();
    // }

    /**
     * 创建时间戳追踪器（用于限流）
     * key: 消息类型，value: 时间戳
     * [已废弃 - 问题23] 已移至RateLimitService内部初始化，不再作为Bean注入
     */
    // @Bean
    // public ConcurrentHashMap<String, Long> messageRateTimestamp() {
    //     return new ConcurrentHashMap<>();
    // }

    /**
     * 配置加载后校验（带降级处理）
     */
    @PostConstruct
    public void validate() {
        int maxMessages = getMaxMessagesPerSecond();
        if (maxMessages < 1) {
            // 配置错误时使用默认值，避免应用启动失败
            logger.warn("限流配置错误：每秒最大消息处理数不能小于1，当前值: {}，将使用默认值100", maxMessages);
            // 设置默认值
            messageProperties.getRateLimit().setMaxMessagesPerSecond(100);
        }
        logger.info("限流配置加载完成，每秒最大消息处理数: {}", getMaxMessagesPerSecond());
    }
}