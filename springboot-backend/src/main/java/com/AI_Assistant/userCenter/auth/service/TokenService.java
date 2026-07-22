package com.AI_Assistant.userCenter.auth.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.AI_Assistant.common.constant.RedisConstants;
import com.AI_Assistant.userCenter.user.dto.UserFullInfoDTO;
import com.AI_Assistant.userCenter.user.entity.User;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.jwt.JWTUtil;
import jakarta.annotation.PostConstruct;

/**
 * Token 服务
 * 负责 Token 的生成、验证和 Redis 存储
 */
@Service
public class TokenService {

    private static final Logger logger = LoggerFactory.getLogger(TokenService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 服务启动初始化
     * 注意：不再使用 redisTemplate.keys() 扫描所有键（这是阻塞操作）
     * 依赖 Redis 的 TTL 机制自动过期旧缓存，提高启动速度和 Redis 性能
     */
    @PostConstruct
    public void init() {
        logger.info("TokenService 初始化完成，旧Token将通过TTL自动过期");
    }

    /**
     * 生成 Token
     * 仅生成 Token 并存储 userId -> token 映射（用于单点登录）
     * 用户完整信息的缓存由 UserCacheService.buildAndCacheFullUserInfo() 统一处理
     */
    public String generateToken(User user) {
        String userId = user.getId();

        // 先清除用户之前的 token（单点登录）
        String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + userId;
        String oldToken = redisTemplate.opsForValue().get(userTokenKey);
        if (oldToken != null) {
            String oldTokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + oldToken;
            redisTemplate.delete(oldTokenKey);
            logger.info("已清除用户旧 token，userId: {}", userId);
        }

        String token = JWTUtil.createToken(
                Map.of("userId", userId, "expireTime", System.currentTimeMillis() + RedisConstants.LOGIN_TOKEN_EXPIRE_MILLIS),
                RedisConstants.JWT_SECRET.getBytes()
        );

        // 存储 userId -> token（用于单点登录）
        redisTemplate.opsForValue().set(userTokenKey, token, RedisConstants.LOGIN_TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);

        logger.info("Token 已生成，用户ID: {}", userId);
        return token;
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        // [已修复 - 问题4] 添加JWT签名验证和过期时间检查
        // 原代码：仅检查Redis中是否存在该key，未验证JWT签名和过期时间
        
        // 1. 先验证JWT签名和过期时间
        try {
            boolean isValid = JWTUtil.verify(token, RedisConstants.JWT_SECRET.getBytes());
            if (!isValid) {
                logger.warn("Token JWT验证失败（签名无效或已过期），token: {}", token != null ? token.substring(0, 20) + "..." : "null");
                return false;
            }
        } catch (Exception e) {
            logger.warn("Token JWT解析异常，token: {}", token != null ? token.substring(0, 20) + "..." : "null", e);
            return false;
        }
        
        // 2. 再检查Redis中是否存在（用于单点登录失效）
        String redisKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
        boolean existsInRedis = Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
        if (!existsInRedis) {
            logger.warn("Token在Redis中不存在（可能已被单点登录踢出），token: {}", token != null ? token.substring(0, 20) + "..." : "null");
            return false;
        }
        
        return true;
    }

    /**
     * 从 Redis 获取用户信息
     */
    public User getUserFromToken(String token) {
        String redisKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;
        String userJson = redisTemplate.opsForValue().get(redisKey);

        if (userJson != null) {
            try {
                // 先反序列化为 UserFullInfoDTO，再从中提取 User
                UserFullInfoDTO fullInfo = JSONUtil.toBean(userJson, UserFullInfoDTO.class);
                return fullInfo.getUser();
            } catch (Exception e) {
                logger.error("反序列化用户信息失败", e);
            }
        }
        return null;
    }

    /**
     * 删除 Token（退出登录）
     */
    public void invalidateToken(String token) {
        String tokenKey = RedisConstants.LOGIN_TOKEN_PREFIX + token;

        // 获取用户信息并删除 userId -> token 映射
        String userJson = redisTemplate.opsForValue().get(tokenKey);
        if (userJson != null) {
            try {
                // 先反序列化为 UserFullInfoDTO，再从中提取 User
                UserFullInfoDTO fullInfo = JSONUtil.toBean(userJson, UserFullInfoDTO.class);
                if (fullInfo != null && fullInfo.getUser() != null) {
                    String userTokenKey = RedisConstants.LOGIN_USER_PREFIX + fullInfo.getUser().getId();
                    redisTemplate.delete(userTokenKey);
                }
            } catch (Exception e) {
                logger.error("清理用户 token 映射失败", e);
            }
        }

        // 删除 token -> 用户信息
        redisTemplate.delete(tokenKey);
        logger.info("Token 已失效，token: {}", token != null ? token.substring(0, 20) + "..." : "null");
    }

    /**
     * 刷新 Token
     */
    public String refreshToken(String oldToken, User user) {
        invalidateToken(oldToken);
        return generateToken(user);
    }
}