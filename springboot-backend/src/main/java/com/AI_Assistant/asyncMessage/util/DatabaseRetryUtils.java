package com.AI_Assistant.asyncMessage.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 数据库操作重试工具类
 * 提供带重试机制的数据库操作执行
 */
public class DatabaseRetryUtils {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseRetryUtils.class);

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    private static final long DEFAULT_RETRY_DELAY_MS = 1000;

    /**
     * 执行带重试的数据库操作
     *
     * @param operation 数据库操作
     * @param <T> 返回类型
     * @return 操作结果
     * @throws RuntimeException 重试次数用尽后仍然失败
     */
    public static <T> T executeWithRetry(Supplier<T> operation) {
        return executeWithRetry(operation, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY_MS);
    }

    /**
     * 执行带重试的数据库操作
     *
     * @param operation 数据库操作
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试间隔（毫秒）
     * @param <T> 返回类型
     * @return 操作结果
     * @throws RuntimeException 重试次数用尽后仍然失败
     */
    public static <T> T executeWithRetry(Supplier<T> operation, int maxRetries, long retryDelayMs) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt <= maxRetries) {
                    logger.warn("数据库操作失败，第 {} 次重试，错误: {}", attempt, e.getMessage());
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("数据库操作重试被中断", ie);
                    }
                }
            }
        }

        logger.error("数据库操作失败，已重试 {} 次，放弃重试", maxRetries, lastException);
        throw new RuntimeException("数据库操作失败，已重试 " + maxRetries + " 次", lastException);
    }

    /**
     * 执行带重试的数据库操作（无返回值）
     *
     * @param operation 数据库操作
     */
    public static void executeWithRetry(Runnable operation) {
        executeWithRetry(operation, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY_MS);
    }

    /**
     * 执行带重试的数据库操作（无返回值）
     *
     * @param operation 数据库操作
     * @param maxRetries 最大重试次数
     * @param retryDelayMs 重试间隔（毫秒）
     */
    public static void executeWithRetry(Runnable operation, int maxRetries, long retryDelayMs) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                operation.run();
                return;
            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt <= maxRetries) {
                    logger.warn("数据库操作失败，第 {} 次重试，错误: {}", attempt, e.getMessage());
                    try {
                        Thread.sleep(retryDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("数据库操作重试被中断", ie);
                    }
                }
            }
        }

        logger.error("数据库操作失败，已重试 {} 次，放弃重试", maxRetries, lastException);
        throw new RuntimeException("数据库操作失败，已重试 " + maxRetries + " 次", lastException);
    }

    /**
     * 执行带降级的数据库操作
     * 如果操作失败，返回降级值
     *
     * @param operation 数据库操作
     * @param fallback 降级值
     * @param <T> 返回类型
     * @return 操作结果或降级值
     */
    public static <T> T executeWithFallback(Supplier<T> operation, T fallback) {
        try {
            return executeWithRetry(operation);
        } catch (Exception e) {
            logger.warn("数据库操作失败，使用降级值，错误: {}", e.getMessage());
            return fallback;
        }
    }

    /**
     * 执行带降级的数据库操作（无返回值）
     * 如果操作失败，执行降级操作
     *
     * @param operation 数据库操作
     * @param fallback 降级操作
     */
    public static void executeWithFallback(Runnable operation, Runnable fallback) {
        try {
            executeWithRetry(operation);
        } catch (Exception e) {
            logger.warn("数据库操作失败，执行降级操作，错误: {}", e.getMessage());
            try {
                fallback.run();
            } catch (Exception fe) {
                logger.error("降级操作执行失败", fe);
            }
        }
    }
}