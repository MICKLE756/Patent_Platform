package com.AI_Assistant.asyncMessage.message.service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 消息模块监控服务
 * 收集和统计消息模块的关键指标
 */
@Service
public class MessageMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(MessageMonitorService.class);

    /**
     * 消息发送成功计数
     */
    private final AtomicLong sendSuccessCount = new AtomicLong(0);

    /**
     * 消息发送失败计数
     */
    private final AtomicLong sendFailedCount = new AtomicLong(0);

    /**
     * 消息消费成功计数
     */
    private final AtomicLong consumeSuccessCount = new AtomicLong(0);

    /**
     * 消息消费失败计数
     */
    private final AtomicLong consumeFailedCount = new AtomicLong(0);

    /**
     * 消息重试计数
     */
    private final AtomicLong retryCount = new AtomicLong(0);

    /**
     * 死信消息计数
     */
    private final AtomicLong deadLetterCount = new AtomicLong(0);

    /**
     * WebSocket连接数
     */
    private final AtomicLong websocketConnectionCount = new AtomicLong(0);

    /**
     * 消息类型统计
     */
    private final ConcurrentHashMap<String, AtomicLong> messageTypeCount = new ConcurrentHashMap<>();

    /**
     * 服务启动时间
     */
    private final LocalDateTime startTime = LocalDateTime.now();

    /**
     * 消费耗时统计（毫秒）
     */
    private final AtomicLong consumeDurationTotal = new AtomicLong(0);
    private final AtomicLong consumeDurationCount = new AtomicLong(0);
    private final AtomicLong consumeDurationMax = new AtomicLong(0);

    /**
     * 重试耗时统计（毫秒）
     */
    private final AtomicLong retryDurationTotal = new AtomicLong(0);
    private final AtomicLong retryDurationCount = new AtomicLong(0);

    /**
     * 线程池监控（线程池名称 -> 线程池引用）
     */
    private final ConcurrentHashMap<String, ThreadPoolExecutor> threadPools = new ConcurrentHashMap<>();

    // ========== 消息发送统计 ==========

    public void recordSendSuccess() {
        sendSuccessCount.incrementAndGet();
    }

    public void recordSendFailed() {
        sendFailedCount.incrementAndGet();
        // 发送失败率超过阈值时告警
        checkSendFailureRate();
    }

    // ========== 消息消费统计 ==========

    public void recordConsumeSuccess() {
        consumeSuccessCount.incrementAndGet();
    }

    public void recordConsumeFailed() {
        consumeFailedCount.incrementAndGet();
        // 消费失败率超过阈值时告警
        checkConsumeFailureRate();
    }

    // ========== 重试和死信统计 ==========

    public void recordRetry() {
        retryCount.incrementAndGet();
        // 重试次数过多时告警
        if (retryCount.get() % 100 == 0) {
            logger.warn("消息重试次数达到: {}", retryCount.get());
        }
    }

    public void recordDeadLetter() {
        deadLetterCount.incrementAndGet();
        logger.error("死信消息计数增加，当前死信总数: {}", deadLetterCount.get());
    }

    // ========== WebSocket连接统计 ==========

    public void incrementWebSocketConnection() {
        websocketConnectionCount.incrementAndGet();
    }

    public void decrementWebSocketConnection() {
        websocketConnectionCount.decrementAndGet();
    }

    // ========== 消息类型统计 ==========

    public void recordMessageType(String messageType) {
        messageTypeCount.computeIfAbsent(messageType, k -> new AtomicLong(0)).incrementAndGet();
    }

    // ========== 耗时统计 ==========

    /**
     * 记录消费耗时
     */
    public void recordConsumeDuration(long durationMs) {
        consumeDurationTotal.addAndGet(durationMs);
        consumeDurationCount.incrementAndGet();
        // 更新最大耗时
        long currentMax;
        do {
            currentMax = consumeDurationMax.get();
            if (durationMs <= currentMax) {
                break;
            }
        } while (!consumeDurationMax.compareAndSet(currentMax, durationMs));
        
        // 耗时超过阈值时告警
        if (durationMs > 5000) { // 超过5秒
            logger.warn("【告警】消息消费耗时过长: {}ms", durationMs);
        }
    }

    /**
     * 记录重试耗时
     */
    public void recordRetryDuration(long durationMs) {
        retryDurationTotal.addAndGet(durationMs);
        retryDurationCount.incrementAndGet();
    }

    /**
     * 获取平均消费耗时
     */
    public double getAverageConsumeDuration() {
        long count = consumeDurationCount.get();
        return count > 0 ? (double) consumeDurationTotal.get() / count : 0;
    }

    /**
     * 获取最大消费耗时
     */
    public long getMaxConsumeDuration() {
        return consumeDurationMax.get();
    }

    // ========== 线程池监控 ==========

    /**
     * 注册线程池进行监控
     */
    public void registerThreadPool(String name, ThreadPoolExecutor executor) {
        threadPools.put(name, executor);
        logger.info("线程池已注册监控: {}", name);
    }

    /**
     * 获取线程池统计信息
     */
    public ThreadPoolStats getThreadPoolStats(String name) {
        ThreadPoolExecutor executor = threadPools.get(name);
        if (executor == null) {
            return null;
        }
        return new ThreadPoolStats(
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount(),
                executor.getTaskCount()
        );
    }

    /**
     * 获取所有线程池统计信息
     */
    public ConcurrentHashMap<String, ThreadPoolStats> getAllThreadPoolStats() {
        ConcurrentHashMap<String, ThreadPoolStats> stats = new ConcurrentHashMap<>();
        threadPools.forEach((name, executor) -> {
            stats.put(name, new ThreadPoolStats(
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getCompletedTaskCount(),
                    executor.getTaskCount()
            ));
        });
        return stats;
    }

    /**
     * 检查线程池状态并告警
     */
    public void checkThreadPoolHealth() {
        threadPools.forEach((name, executor) -> {
            int activeCount = executor.getActiveCount();
            int poolSize = executor.getPoolSize();
            int queueSize = executor.getQueue().size();

            // 活跃线程数超过线程池大小的80%
            if (poolSize > 0 && activeCount > poolSize * 0.8) {
                logger.warn("【告警】线程池活跃线程数过高: {}, 活跃: {}, 总数: {}", name, activeCount, poolSize);
            }

            // 队列积压超过100
            if (queueSize > 100) {
                logger.warn("【告警】线程池队列积压过多: {}, 队列大小: {}", name, queueSize);
            }
        });
    }

    // ========== 告警检查 ==========

    private void checkSendFailureRate() {
        long total = sendSuccessCount.get() + sendFailedCount.get();
        if (total > 100) {
            double failureRate = (double) sendFailedCount.get() / total;
            if (failureRate > 0.1) { // 失败率超过10%
                logger.error("【告警】消息发送失败率过高: {}%", String.format("%.2f", failureRate * 100));
            }
        }
    }

    private void checkConsumeFailureRate() {
        long total = consumeSuccessCount.get() + consumeFailedCount.get();
        if (total > 100) {
            double failureRate = (double) consumeFailedCount.get() / total;
            if (failureRate > 0.1) { // 失败率超过10%
                logger.error("【告警】消息消费失败率过高: {}%", String.format("%.2f", failureRate * 100));
            }
        }
    }

    // ========== 获取统计信息 ==========

    public MonitorStats getStats() {
        return new MonitorStats(
                sendSuccessCount.get(),
                sendFailedCount.get(),
                consumeSuccessCount.get(),
                consumeFailedCount.get(),
                retryCount.get(),
                deadLetterCount.get(),
                websocketConnectionCount.get(),
                startTime
        );
    }

    /**
     * 监控统计数据
     */
    public record MonitorStats(
            long sendSuccessCount,
            long sendFailedCount,
            long consumeSuccessCount,
            long consumeFailedCount,
            long retryCount,
            long deadLetterCount,
            long websocketConnectionCount,
            LocalDateTime startTime
    ) {
        public double getSendFailureRate() {
            long total = sendSuccessCount + sendFailedCount;
            return total > 0 ? (double) sendFailedCount / total : 0;
        }

        public double getConsumeFailureRate() {
            long total = consumeSuccessCount + consumeFailedCount;
            return total > 0 ? (double) consumeFailedCount / total : 0;
        }
    }

    /**
     * 线程池统计数据
     */
    public record ThreadPoolStats(
            int poolSize,
            int activeCount,
            int queueSize,
            long completedTaskCount,
            long taskCount
    ) {
        public double getActiveRate() {
            return poolSize > 0 ? (double) activeCount / poolSize : 0;
        }
    }
}