package com.AI_Assistant.backend.statistics.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.AI_Assistant.backend.statistics.service.StatisticsService;
import com.AI_Assistant.common.constant.RedisConstants;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统计数据初始化器 - 在应用启动时预加载统计数据到Redis缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsDataInitializer implements ApplicationRunner {

    private final StatisticsService statisticsService;
    private final com.AI_Assistant.userCenter.user.mapper.UserMapper userMapper;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("========== 开始预加载统计数据到Redis缓存 ==========");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 预加载今日新增用户数
            preloadTodayNewUsers();

            // 2. 预加载今日活跃用户数
            preloadTodayActiveUsers();

            // 3. 预加载用户总数
            preloadTotalUsers();

            // 4. 预加载企业总数
            preloadTotalEnterprises();

            // 5. 预加载科研团队总数
            preloadTotalResearchTeams();

            // 6. 预加载近7天活跃用户历史数据
            preloadActiveUserHistory();

            // 7. 预加载热门搜索关键词
            preloadHotSearchKeywords();

            // 8. 预加载用户统计概览
            preloadStatisticsOverview();

            long endTime = System.currentTimeMillis();
            log.info("========== 统计数据预加载完成，耗时: {} ms ==========", (endTime - startTime));

        } catch (Exception e) {
            log.error("统计数据预加载失败", e);
            // 继续启动，不影响应用运行
        }
    }

    /**
     * 预加载今日新增用户数
     */
    private void preloadTodayNewUsers() {
        try {
            long count = statisticsService.getTodayNewUsers();
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "today_new_users", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("预加载今日新增用户数: {}", count);
        } catch (Exception e) {
            log.warn("预加载今日新增用户数失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载今日活跃用户数
     */
    private void preloadTodayActiveUsers() {
        try {
            long count = statisticsService.getTodayActiveUsers();
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "today_active_users", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("预加载今日活跃用户数: {}", count);
        } catch (Exception e) {
            log.warn("预加载今日活跃用户数失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载用户总数
     */
    private void preloadTotalUsers() {
        try {
            long count = statisticsService.getTotalUsers();
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "total_users", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("预加载用户总数: {}", count);
        } catch (Exception e) {
            log.warn("预加载用户总数失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载企业总数
     */
    private void preloadTotalEnterprises() {
        try {
            long count = statisticsService.getTotalEnterprises();
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "total_enterprises", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("预加载企业总数: {}", count);
        } catch (Exception e) {
            log.warn("预加载企业总数失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载科研团队总数
     */
    private void preloadTotalResearchTeams() {
        try {
            long count = statisticsService.getTotalResearchTeams();
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "total_research_teams", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("预加载科研团队总数: {}", count);
        } catch (Exception e) {
            log.warn("预加载科研团队总数失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载近7天活跃用户历史数据
     */
    private void preloadActiveUserHistory() {
        try {
            List<Map<String, Object>> history = statisticsService.getRecentActiveUserHistory(7);
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "active_user_history", history,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("预加载活跃用户历史数据: {} 条", history.size());
        } catch (Exception e) {
            log.warn("预加载活跃用户历史数据失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载热门搜索关键词
     */
    private void preloadHotSearchKeywords() {
        try {
            List<Map<String, Object>> keywords = statisticsService.getHotSearchKeywordsWithCache(7);
            log.info("预加载热门搜索关键词: {} 条", keywords.size());
        } catch (Exception e) {
            log.warn("预加载热门搜索关键词失败: {}", e.getMessage());
        }
    }

    /**
     * 预加载统计概览数据（整合所有统计数据）
     */
    private void preloadStatisticsOverview() {
        try {
            Map<String, Object> overview = new java.util.HashMap<>();
            overview.put("todayNewUsers", statisticsService.getTodayNewUsers());
            overview.put("todayActiveUsers", statisticsService.getTodayActiveUsers());
            overview.put("totalUsers", statisticsService.getTotalUsers());
            overview.put("totalEnterprises", statisticsService.getTotalEnterprises());
            overview.put("totalResearchTeams", statisticsService.getTotalResearchTeams());
            overview.put("normalUserCount", statisticsService.getNormalUserCount());
            overview.put("adminUserCount", statisticsService.getAdminUserCount());
            overview.put("activeUserHistory", statisticsService.getRecentActiveUserHistory(7));
            overview.put("hotKeywords", statisticsService.getHotSearchKeywords(7));
            
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "overview", overview,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("预加载统计概览数据完成");
        } catch (Exception e) {
            log.warn("预加载统计概览数据失败: {}", e.getMessage());
        }
    }
}