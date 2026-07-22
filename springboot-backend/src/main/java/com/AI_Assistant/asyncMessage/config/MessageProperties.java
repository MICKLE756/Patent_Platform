package com.AI_Assistant.asyncMessage.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 消息服务统一配置属性类
 * 集中管理所有消息相关的配置参数，避免硬编码
 */
@Component
@ConfigurationProperties(prefix = "ai-assistant.message")
public class MessageProperties {

    private static final Logger logger = LoggerFactory.getLogger(MessageProperties.class);

    /**
     * RabbitMQ队列配置
     */
    private Queue queue = new Queue();

    /**
     * 消息重试配置
     */
    private Retry retry = new Retry();

    /**
     * WebSocket配置
     */
    private WebSocket websocket = new WebSocket();

    /**
     * 限流配置
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * 查询配置
     */
    private Query query = new Query();

    /**
     * 队列配置内部类
     */
    public static class Queue {
        /**
         * 意向留言队列最大长度
         */
        private int intentionMaxLength = 10000;

        /**
         * 系统通知队列最大长度
         */
        private int noticeMaxLength = 10000;

        /**
         * 审批任务队列最大长度
         */
        private int auditMaxLength = 5000;

        /**
         * 广播消息队列最大长度
         */
        private int broadcastMaxLength = 10000;

        /**
         * 死信队列名称
         */
        private String deadLetterQueueName = "message.dlx.q";

        public int getIntentionMaxLength() {
            return intentionMaxLength;
        }

        public void setIntentionMaxLength(int intentionMaxLength) {
            this.intentionMaxLength = intentionMaxLength;
        }

        public int getNoticeMaxLength() {
            return noticeMaxLength;
        }

        public void setNoticeMaxLength(int noticeMaxLength) {
            this.noticeMaxLength = noticeMaxLength;
        }

        public int getAuditMaxLength() {
            return auditMaxLength;
        }

        public void setAuditMaxLength(int auditMaxLength) {
            this.auditMaxLength = auditMaxLength;
        }

        public int getBroadcastMaxLength() {
            return broadcastMaxLength;
        }

        public void setBroadcastMaxLength(int broadcastMaxLength) {
            this.broadcastMaxLength = broadcastMaxLength;
        }

        public String getDeadLetterQueueName() {
            return deadLetterQueueName;
        }

        public void setDeadLetterQueueName(String deadLetterQueueName) {
            this.deadLetterQueueName = deadLetterQueueName;
        }
    }

    /**
     * 重试配置内部类
     */
    public static class Retry {
        /**
         * 最大重试次数
         */
        private int maxRetries = 3;

        /**
         * 重试延迟时间（分钟）
         */
        private int retryDelayMinutes = 5;

        /**
         * 消息投递记录保留天数
         */
        private int retentionDays = 30;

        /**
         * [已修复 - 问题67] WebSocket推送重试基础延迟（毫秒）
         */
        private int webSocketRetryDelayMs = 100;

        /**
         * [已修复 - 问题67] WebSocket推送最大重试次数
         */
        private int webSocketMaxRetries = 3;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getRetryDelayMinutes() {
            return retryDelayMinutes;
        }

        public void setRetryDelayMinutes(int retryDelayMinutes) {
            this.retryDelayMinutes = retryDelayMinutes;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public int getWebSocketRetryDelayMs() {
            return webSocketRetryDelayMs;
        }

        public void setWebSocketRetryDelayMs(int webSocketRetryDelayMs) {
            this.webSocketRetryDelayMs = webSocketRetryDelayMs;
        }

        public int getWebSocketMaxRetries() {
            return webSocketMaxRetries;
        }

        public void setWebSocketMaxRetries(int webSocketMaxRetries) {
            this.webSocketMaxRetries = webSocketMaxRetries;
        }
    }

    /**
     * WebSocket配置内部类
     */
    public static class WebSocket {
        /**
         * 心跳超时时间（秒）
         */
        private long heartbeatTimeoutSeconds = 60;

        /**
         * 心跳检查间隔（秒）
         */
        private long heartbeatCheckIntervalSeconds = 30;

        /**
         * Token定期验证间隔（秒），0表示不进行定期验证
         */
        private long tokenValidationIntervalSeconds = 60;

        /**
         * 单用户最大设备连接数
         */
        private int maxDevicesPerUser = 5;

        /**
         * 系统最大WebSocket连接数
         */
        private int maxTotalConnections = 1000;

        /**
         * WebSocket发送超时时间（毫秒）
         */
        private long sendTimeoutMs = 1000;

        /**
         * WebSocket发送缓冲区大小（字节）
         */
        private int bufferSize = 1024 * 1024;

        /**
         * 允许的WebSocket来源白名单
         * 生产环境应配置具体域名，如: ["https://example.com", "https://www.example.com"]
         */
        private java.util.List<String> allowedOrigins = java.util.Arrays.asList("http://localhost:8080", "http://localhost:3000");

        public long getHeartbeatTimeoutSeconds() {
            return heartbeatTimeoutSeconds;
        }

        public void setHeartbeatTimeoutSeconds(long heartbeatTimeoutSeconds) {
            this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        }

        public long getHeartbeatCheckIntervalSeconds() {
            return heartbeatCheckIntervalSeconds;
        }

        public void setHeartbeatCheckIntervalSeconds(long heartbeatCheckIntervalSeconds) {
            this.heartbeatCheckIntervalSeconds = heartbeatCheckIntervalSeconds;
        }

        public long getTokenValidationIntervalSeconds() {
            return tokenValidationIntervalSeconds;
        }

        public void setTokenValidationIntervalSeconds(long tokenValidationIntervalSeconds) {
            this.tokenValidationIntervalSeconds = tokenValidationIntervalSeconds;
        }

        public int getMaxDevicesPerUser() {
            return maxDevicesPerUser;
        }

        public void setMaxDevicesPerUser(int maxDevicesPerUser) {
            this.maxDevicesPerUser = maxDevicesPerUser;
        }

        public int getMaxTotalConnections() {
            return maxTotalConnections;
        }

        public void setMaxTotalConnections(int maxTotalConnections) {
            this.maxTotalConnections = maxTotalConnections;
        }

        public long getSendTimeoutMs() {
            return sendTimeoutMs;
        }

        public void setSendTimeoutMs(long sendTimeoutMs) {
            this.sendTimeoutMs = sendTimeoutMs;
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public void setBufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
        }

        public java.util.List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(java.util.List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /**
     * 限流配置内部类
     */
    public static class RateLimit {
        /**
         * 每秒最大消息处理数
         */
        private int maxMessagesPerSecond = 100;

        public int getMaxMessagesPerSecond() {
            return maxMessagesPerSecond;
        }

        public void setMaxMessagesPerSecond(int maxMessagesPerSecond) {
            this.maxMessagesPerSecond = maxMessagesPerSecond;
        }
    }

    /**
     * 查询配置内部类
     */
    public static class Query {
        /**
         * 查询结果最大限制数
         */
        private int maxLimit = 100;

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public WebSocket getWebsocket() {
        return websocket;
    }

    public void setWebsocket(WebSocket websocket) {
        this.websocket = websocket;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Query getQuery() {
        return query;
    }

    public void setQuery(Query query) {
        this.query = query;
    }

    /**
     * 配置加载后校验
     */
    @PostConstruct
    public void validate() {
        // 队列配置校验
        if (queue.getIntentionMaxLength() < 100) {
            throw new IllegalArgumentException("队列最大长度不能小于100，当前值: " + queue.getIntentionMaxLength());
        }
        if (queue.getNoticeMaxLength() < 100) {
            throw new IllegalArgumentException("队列最大长度不能小于100，当前值: " + queue.getNoticeMaxLength());
        }
        if (queue.getAuditMaxLength() < 100) {
            throw new IllegalArgumentException("队列最大长度不能小于100，当前值: " + queue.getAuditMaxLength());
        }
        if (queue.getBroadcastMaxLength() < 100) {
            throw new IllegalArgumentException("队列最大长度不能小于100，当前值: " + queue.getBroadcastMaxLength());
        }
        if (queue.getDeadLetterQueueName() == null || queue.getDeadLetterQueueName().trim().isEmpty()) {
            throw new IllegalArgumentException("死信队列名称不能为空");
        }

        // 重试配置校验
        if (retry.getMaxRetries() < 1) {
            throw new IllegalArgumentException("最大重试次数不能小于1，当前值: " + retry.getMaxRetries());
        }

        // WebSocket配置校验
        if (websocket.getHeartbeatTimeoutSeconds() < 10) {
            throw new IllegalArgumentException("心跳超时时间不能小于10秒，当前值: " + websocket.getHeartbeatTimeoutSeconds());
        }
        if (websocket.getHeartbeatCheckIntervalSeconds() < 5) {
            throw new IllegalArgumentException("心跳检查间隔不能小于5秒，当前值: " + websocket.getHeartbeatCheckIntervalSeconds());
        }
        if (websocket.getMaxDevicesPerUser() < 1) {
            throw new IllegalArgumentException("单用户最大设备连接数不能小于1，当前值: " + websocket.getMaxDevicesPerUser());
        }
        if (websocket.getMaxTotalConnections() < 10) {
            throw new IllegalArgumentException("系统最大WebSocket连接数不能小于10，当前值: " + websocket.getMaxTotalConnections());
        }

        // 限流配置校验
        if (rateLimit.getMaxMessagesPerSecond() < 1) {
            throw new IllegalArgumentException("每秒最大消息处理数不能小于1，当前值: " + rateLimit.getMaxMessagesPerSecond());
        }

        // 查询配置校验
        if (query.getMaxLimit() < 10) {
            throw new IllegalArgumentException("查询结果最大限制数不能小于10，当前值: " + query.getMaxLimit());
        }

        // 输出配置信息
        logger.info("消息服务配置加载完成：");
        logger.info("  - 队列配置：");
        logger.info("    * 意向留言队列最大长度: {}", queue.getIntentionMaxLength());
        logger.info("    * 系统通知队列最大长度: {}", queue.getNoticeMaxLength());
        logger.info("    * 审批任务队列最大长度: {}", queue.getAuditMaxLength());
        logger.info("    * 广播消息队列最大长度: {}", queue.getBroadcastMaxLength());
        logger.info("    * 死信队列名称: {}", queue.getDeadLetterQueueName());
        logger.info("  - 重试配置：");
        logger.info("    * 最大重试次数: {}", retry.getMaxRetries());
        logger.info("  - WebSocket配置：");
        logger.info("    * 心跳超时时间: {}秒", websocket.getHeartbeatTimeoutSeconds());
        logger.info("    * 心跳检查间隔: {}秒", websocket.getHeartbeatCheckIntervalSeconds());
        logger.info("    * 单用户最大设备数: {}", websocket.getMaxDevicesPerUser());
        logger.info("    * 系统最大连接数: {}", websocket.getMaxTotalConnections());
        logger.info("  - 限流配置：");
        logger.info("    * 每秒最大消息处理数: {}", rateLimit.getMaxMessagesPerSecond());
        logger.info("  - 查询配置：");
        logger.info("    * 查询结果最大限制数: {}", query.getMaxLimit());
    }
}