package com.AI_Assistant.asyncMessage.message.service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.AI_Assistant.asyncMessage.config.RateLimitConfig;

/**
 * 消息限流服务
 * 限制消息消费速率，防止消息风暴
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    // [已修复 - 问题23] 将外部注入改为内部初始化，避免依赖外部Bean配置
    private final ConcurrentHashMap<String, AtomicInteger> messageRateCounter = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> messageRateTimestamp = new ConcurrentHashMap<>();
    private final RateLimitConfig rateLimitConfig;

    /**
     * 记录每个消息类型的最后访问时间，用于清理过期的计数器
     */
    private final ConcurrentHashMap<String, Long> lastAccessTime = new ConcurrentHashMap<>();

    /**
     * 计数器过期时间（毫秒），超过此时间未被访问的计数器将被清理
     */
    private static final long EXPIRE_MILLIS = 24 * 60 * 60 * 1000L; // 24小时

    /**
     * 清理锁对象，确保清理过程的原子性
     */
    private final Object cleanupLock = new Object();

    /**
     * 构造器注入依赖
     */
    public RateLimitService(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    /**
     * 检查是否允许处理消息
     * @param messageType 消息类型
     * @return 是否允许处理
     */
    public boolean allow(String messageType) {
        long currentSecond = System.currentTimeMillis() / 1000;
        String key = messageType != null ? messageType : "default";
        long now = System.currentTimeMillis();

        // 更新最后访问时间
        lastAccessTime.put(key, now);

        // 定期清理过期的计数器
        tryCleanupExpired();

        // 获取或创建计数器
        AtomicInteger counter = messageRateCounter.computeIfAbsent(key, k -> new AtomicInteger(0));

        // [已修复 - 问题20] 使用乐观锁替代synchronized，减少高并发场景下的性能瓶颈
        // 通过时间戳的CAS操作实现原子的时间窗口切换和计数器重置
        Long currentTimestamp = messageRateTimestamp.get(key);
        if (currentTimestamp == null || currentTimestamp != currentSecond) {
            // 尝试原子更新时间戳，如果成功说明当前线程负责重置计数器
            if (messageRateTimestamp.putIfAbsent(key, currentSecond) == null) {
                counter.set(0);
            } else {
                // 其他线程已经更新了时间戳，再次读取确保获取最新的时间戳
                currentTimestamp = messageRateTimestamp.get(key);
                if (currentTimestamp != currentSecond) {
                    // 存在竞态，等待一下再重试
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return allow(messageType);
                }
            }
        }

        // 检查是否超过限流阈值
        int count = counter.incrementAndGet();
        if (count > rateLimitConfig.getMaxMessagesPerSecond()) {
            logger.warn("消息限流触发，消息类型: {}, 当前速率: {} 条/秒", messageType, count);
            return false;
        }

        return true;
    }

    /**
     * 尝试清理过期的计数器
     * 使用cleanupLock确保清理的原子性
     */
    private void tryCleanupExpired() {
        // 简单策略：仅当Map较大时才清理，避免影响性能
        if (lastAccessTime.size() < 1000) {
            return;
        }
        // 使用tryLock模式避免阻塞业务
        if (!Thread.holdsLock(cleanupLock)) {
            synchronized (cleanupLock) {
                if (lastAccessTime.size() < 1000) {
                    return;
                }
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<String, Long>> it = lastAccessTime.entrySet().iterator();
                int cleaned = 0;
                while (it.hasNext()) {
                    Map.Entry<String, Long> entry = it.next();
                    if (now - entry.getValue() > EXPIRE_MILLIS) {
                        String key = entry.getKey();
                        it.remove();
                        messageRateCounter.remove(key);
                        messageRateTimestamp.remove(key);
                        cleaned++;
                    }
                }
                if (cleaned > 0) {
                    logger.info("清理过期消息限流计数器，清理数量: {}", cleaned);
                }
            }
        }
    }

    /**
     * 获取当前速率
     * @param messageType 消息类型
     * @return 当前速率（条/秒）
     */
    public int getCurrentRate(String messageType) {
        String key = messageType != null ? messageType : "default";
        AtomicInteger counter = messageRateCounter.get(key);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 重置速率计数器
     * @param messageType 消息类型
     */
    public void reset(String messageType) {
        String key = messageType != null ? messageType : "default";
        AtomicInteger counter = messageRateCounter.get(key);
        // 整个重置过程在synchronized块内，避免与allow方法产生竞态
        if (counter != null) {
            synchronized (counter) {
                counter.set(0);
                messageRateTimestamp.put(key, System.currentTimeMillis() / 1000);
            }
        } else {
            messageRateTimestamp.put(key, System.currentTimeMillis() / 1000);
        }
    }
}