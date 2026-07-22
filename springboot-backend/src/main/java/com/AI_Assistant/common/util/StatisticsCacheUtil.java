package com.AI_Assistant.common.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.AI_Assistant.common.constant.RedisConstants;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统计数据缓存工具类 - 用于维护缓存一致性
 * 采用方案一（较长过期时间）+ 方案三（异步预热）的结合
 * 
 * 注意：此类只负责清除缓存，不负责重建缓存
 * 缓存重建由 StatisticsService 提供异步方法
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsCacheUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 清除所有统计数据缓存
     * 注意：StatisticsService 会异步重建这些缓存
     */
    public void clearAllStatisticsCache() {
        try {
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "today_new_users");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "today_active_users");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "total_users");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "total_enterprises");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "total_research_teams");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "active_user_history");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "overview");
            log.info("已清除所有统计数据缓存");
        } catch (Exception e) {
            log.error("清除统计数据缓存失败", e);
        }
    }

    /**
     * 清除用户相关统计缓存
     */
    public void clearUserStatisticsCache() {
        try {
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "today_new_users");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "today_active_users");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "total_users");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "active_user_history");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "overview");
            log.info("已清除用户相关统计缓存");
        } catch (Exception e) {
            log.error("清除用户统计缓存失败", e);
        }
    }

    /**
     * 清除企业相关统计缓存
     */
    public void clearEnterpriseStatisticsCache() {
        try {
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "total_enterprises");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "overview");
            log.info("已清除企业相关统计缓存");
        } catch (Exception e) {
            log.error("清除企业统计缓存失败", e);
        }
    }

    /**
     * 清除科研团队相关统计缓存
     */
    public void clearResearchTeamStatisticsCache() {
        try {
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "total_research_teams");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "overview");
            log.info("已清除科研团队相关统计缓存");
        } catch (Exception e) {
            log.error("清除科研团队统计缓存失败", e);
        }
    }

    /**
     * 清除活跃用户统计缓存
     */
    public void clearActiveUserStatisticsCache() {
        try {
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "today_active_users");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "active_user_history");
            redisTemplate.delete(RedisConstants.STATISTICS_PREFIX + "overview");
            log.info("已清除活跃用户统计缓存");
        } catch (Exception e) {
            log.error("清除活跃用户统计缓存失败", e);
        }
    }

    /**
     * 清除热门搜索关键词缓存
     */
    public void clearHotSearchKeywordsCache() {
        try {
            redisTemplate.delete(RedisConstants.HOT_SEARCH_KEYWORDS_KEY);
            log.info("已清除热门搜索关键词缓存");
        } catch (Exception e) {
            log.error("清除热门搜索关键词缓存失败", e);
        }
    }
}