package com.AI_Assistant.common.constant;

/**
 * Redis 常量类
 * 存放 Redis key 前缀、过期时间等常量
 */
public final class RedisConstants {

    private RedisConstants() {
        // 私有构造函数，防止实例化
    }

    // ==================== Token 相关 ====================
    
    /**
     * Token 前缀
     * 完整 key: login:token:{token}
     */
    public static final String LOGIN_TOKEN_PREFIX = "login:token:";

    /**
     * Token 过期时间（秒）- 30分钟
     */
    public static final long LOGIN_TOKEN_EXPIRE_SECONDS = 30 * 60;

    /**
     * Token 过期时间（毫秒）- 30分钟
     */
    public static final long LOGIN_TOKEN_EXPIRE_MILLIS = LOGIN_TOKEN_EXPIRE_SECONDS * 1000L;

    /**
     * JWT 签名密钥（Base64编码后长度至少32字符）
     * 生产环境应从配置文件读取，此处为演示使用固定密钥
     */
    public static final String JWT_SECRET = "AI_Patent_Assistant_2024_JWT_Signature_Secret_Key";

    /**
     * 无效 Token 前缀（用于缓存穿透防护）
     * 完整 key: login:invalid_token:{token}
     */
    public static final String LOGIN_INVALID_TOKEN_PREFIX = "login:invalid_token:";

    /**
     * 无效 Token 缓存时间（秒）- 30秒
     */
    public static final long LOGIN_INVALID_TOKEN_EXPIRE_SECONDS = 30;

    // ==================== 用户相关 ====================

    /**
     * 用户信息前缀
     * 完整 key: login:user:{userId}
     */
    public static final String LOGIN_USER_PREFIX = "login:user:";

    /**
     * 用户信息过期时间（秒）- 24小时
     */
    public static final long LOGIN_USER_EXPIRE_SECONDS = 24 * 60 * 60;

    // ==================== 验证码相关 ====================

    /**
     * 短信验证码前缀
     * 完整 key: sms:code:{phone}
     */
    public static final String SMS_CODE_PREFIX = "sms:code:";

    /**
     * 短信验证码过期时间（秒）- 5分钟
     */
    public static final long SMS_CODE_EXPIRE_SECONDS = 5 * 60;

    /**
     * 邮箱验证码前缀
     * 完整 key: email:code:{email}
     */
    public static final String EMAIL_CODE_PREFIX = "email:code:";

    /**
     * 邮箱验证码过期时间（秒）- 10分钟
     */
    public static final long EMAIL_CODE_EXPIRE_SECONDS = 10 * 60;

    // ==================== 限流相关 ====================

    /**
     * 接口限流前缀
     * 完整 key: rate:limit:{ip}:{uri}
     */
    public static final String RATE_LIMIT_PREFIX = "rate:limit:";

    /**
     * 限流过期时间（秒）- 1分钟
     */
    public static final long RATE_LIMIT_EXPIRE_SECONDS = 60;

    // ==================== 锁相关 ====================

    /**
     * 分布式锁前缀
     * 完整 key: lock:{resource}
     */
    public static final String LOCK_PREFIX = "lock:";

    /**
     * 分布式锁过期时间（秒）- 30秒
     */
    public static final long LOCK_EXPIRE_SECONDS = 30;

    // ==================== 搜索去重相关 ====================

    /**
     * 搜索去重前缀
     * 完整 key: search:dedup:{userId}:{searchKey}
     */
    public static final String SEARCH_DEDUP_PREFIX = "search:dedup:";

    /**
     * 搜索去重过期时间（秒）- 5分钟
     * 同一用户在5分钟内对同一搜索条件的搜索只计数一次
     */
    public static final long SEARCH_DEDUP_EXPIRE_SECONDS = 5 * 60;

    // ==================== 统计缓存相关 ====================

    /**
     * 统计数据键名前缀
     */
    public static final String STATISTICS_PREFIX = "statistics:";

    /**
     * 热门搜索关键词缓存key
     */
    public static final String HOT_SEARCH_KEYWORDS_KEY = "statistics:hot_search_keywords";

    /**
     * 统计数据缓存过期时间（秒）- 10分钟
     */
    public static final long STATISTICS_CACHE_EXPIRE_SECONDS = 10 * 60;

    // ==================== 已绑定专利缓存相关 ====================

    /**
     * 用户已绑定专利ID列表缓存前缀
     * 完整 key: login:bound_patents:{userId}
     */
    public static final String LOGIN_BOUND_PATENTS_PREFIX = "login:bound_patents:";

    /**
     * 已绑定专利缓存过期时间（秒）- 与 Token 同步过期（30分钟）
     */
    public static final long LOGIN_BOUND_PATENTS_EXPIRE_SECONDS = LOGIN_TOKEN_EXPIRE_SECONDS;

    // ==================== 科研团队信息缓存相关 ====================

    /**
     * 科研团队信息缓存前缀
     * 完整 key: login:team:{userId}
     */
    public static final String LOGIN_TEAM_PREFIX = "login:team:";

    /**
     * 科研团队信息缓存过期时间（秒）- 与 Token 同步过期（30分钟）
     */
    public static final long LOGIN_TEAM_EXPIRE_SECONDS = LOGIN_TOKEN_EXPIRE_SECONDS;

    // ==================== 活跃用户统计相关 ====================

    /**
     * 今日活跃用户 Set 缓存前缀
     * 完整 key: statistics:active_users:{日期}  例如：statistics:active_users:2024-06-15
     * 使用 Set 存储已登录的用户ID，避免重复统计
     */
    public static final String STATISTICS_ACTIVE_USERS_PREFIX = "statistics:active_users:";

    /**
     * 活跃用户 Set 过期时间（秒）- 48小时
     * 保留2天足够覆盖跨日统计需求
     */
    public static final long STATISTICS_ACTIVE_USERS_EXPIRE_SECONDS = 48 * 60 * 60;
}