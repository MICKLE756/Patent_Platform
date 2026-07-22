package com.AI_Assistant.backend.statistics.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.backend.patent.service.PatentService;
import com.AI_Assistant.backend.statistics.service.StatisticsService;
import com.AI_Assistant.common.Result;

/**
 * 统计数据控制器
 * 
 * 登录验证由 LoginRequiredInterceptor 统一处理
 */
@RestController
@RequestMapping("/api/v1")
public class StatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsController.class);

    @Autowired
    private PatentService patentService;

    @Autowired
    private StatisticsService statisticsService;

    /**
     * 获取今日活跃用户数（基于统计聚合表）
     * 公开接口，无需登录
     */
    @GetMapping("/statistics/todayActiveUsers")
    public Result<Map<String, Object>> getTodayActiveUsers() {
        long count = statisticsService.getTodayActiveUsers();
        return Result.ok(Map.of("count", count));
    }

    /**
     * 获取今日新增用户数（基于统计聚合表）
     * 公开接口，无需登录
     */
    @GetMapping("/statistics/todayNewUsers")
    public Result<Map<String, Object>> getTodayNewUsers() {
        long count = statisticsService.getTodayNewUsers();
        return Result.ok(Map.of("count", count));
    }

    /**
     * 获取专利总数
     * 公开接口，无需登录
     */
    @GetMapping("/statistics/totalPatents")
    public Result<Map<String, Object>> getTotalPatents() {
        int count = patentService.getTotalCount();
        return Result.ok(Map.of("count", count));
    }

    /**
     * 获取用户历史统计（最近7天新增用户，基于聚合表）
     * 公开接口，无需登录
     */
    @GetMapping("/statistics/userHistory")
    public Result<List<Map<String, Object>>> getUserHistory() {
        List<Map<String, Object>> history = new ArrayList<>();
        
        List<Map<String, Object>> statsHistory = statisticsService.getRecentActiveUserHistory(7);
        
        for (Map<String, Object> dailyStats : statsHistory) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", dailyStats.get("date"));
            item.put("count", dailyStats.get("newUserCount"));
            history.add(item);
        }

        return Result.ok(history);
    }

    /**
     * 获取最近N天活跃用户数据
     * 公开接口，无需登录
     * @param days 天数（默认7天）
     */
    @GetMapping("/statistics/recentActiveUsers")
    public Result<List<Map<String, Object>>> getRecentActiveUsers(
            @RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> history = statisticsService.getRecentActiveUserHistory(days);
        return Result.ok(history);
    }

    /**
     * 获取热门搜索关键词（基于近7天搜索频率）
     * 公开接口，无需登录
     */
    @GetMapping("/statistics/hotSearchKeywords")
    public Result<List<Map<String, Object>>> getHotSearchKeywords() {
        return Result.ok(statisticsService.getHotSearchKeywordsWithCache(7));
    }

    /**
     * 获取热门专利（按浏览次数排序，直接从BoundPatent表查询）
     * 公开接口，无需登录
     */
    @GetMapping("/statistics/hotPatents")
    public Result<List<Map<String, Object>>> getHotPatents() {
        List<Map<String, Object>> hotPatents = patentService.getHotBoundPatents(10);
        return Result.ok(hotPatents);
    }

    /**
     * 获取待审核专利数
     * 权限验证由 PermissionInterceptor 统一处理（需要 STATISTICS_VIEW 权限）
     */
    @GetMapping("/statistics/pendingPatents")
    public Result<Map<String, Object>> getPendingPatents() {
        logger.info("查询待审核专利数");
        return Result.ok(Map.of("count", 0));
    }

    /**
     * 获取活跃用户历史（最近7天）
     * 公开接口，无需登录
     */
    @GetMapping("/statistics/activeUserHistory")
    public Result<List<Map<String, Object>>> getActiveUserHistory() {
        List<Map<String, Object>> history = statisticsService.getRecentActiveUserHistory(7);
        return Result.ok(history);
    }

    /**
     * 获取用户类型统计
     * 权限验证由 PermissionInterceptor 统一处理（需要 STATISTICS_VIEW 权限）
     */
    @GetMapping("/statistics/userTypes")
    public Result<Map<String, Object>> getUserTypeStatistics() {
        logger.info("查询用户类型统计");
        
        Map<String, Object> stats = new HashMap<>();

        long total = statisticsService.getTotalUsers();
        stats.put("total", total);

        stats.put("normalUsers", statisticsService.getNormalUserCount());
        stats.put("enterpriseUsers", statisticsService.getTotalEnterprises());
        stats.put("researchTeamUsers", statisticsService.getTotalResearchTeams());
        stats.put("adminUsers", statisticsService.getAdminUserCount());

        return Result.ok(stats);
    }

    /**
     * 获取企业统计
     * 权限验证由 PermissionInterceptor 统一处理（需要 STATISTICS_VIEW 权限）
     */
    @GetMapping("/statistics/enterprises")
    public Result<Map<String, Object>> getEnterpriseStatistics() {
        logger.info("查询企业统计");
        
        Map<String, Object> stats = new HashMap<>();
        
        long total = statisticsService.getTotalEnterprises();
        stats.put("total", total);

        return Result.ok(stats);
    }

    /**
     * 获取科研团队统计
     * 权限验证由 PermissionInterceptor 统一处理（需要 STATISTICS_VIEW 权限）
     */
    @GetMapping("/statistics/researchTeams")
    public Result<Map<String, Object>> getResearchTeamStatistics() {
        logger.info("查询科研团队统计");
        
        Map<String, Object> stats = new HashMap<>();
        
        long total = statisticsService.getTotalResearchTeams();
        stats.put("total", total);

        return Result.ok(stats);
    }
}