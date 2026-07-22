package com.AI_Assistant.backend.admin.controller;

import java.time.LocalDate;
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
import com.AI_Assistant.backend.statistics.entity.UserStatistics;
import com.AI_Assistant.backend.statistics.mapper.UserStatisticsMapper;
import com.AI_Assistant.backend.statistics.service.StatisticsService;
import com.AI_Assistant.common.Result;

/**
 * 管理员统计控制器
 * 提供管理员专属的统计查看接口
 * 
 * 注意：此类需要权限验证，普通用户无法访问
 * 所需权限：permission_statistics_view = 1
 * 权限验证由 PermissionInterceptor 统一处理
 */
@RestController
@RequestMapping("/api/v1/admin/statistics")
public class AdminStatisticsController {

    private static final Logger logger = LoggerFactory.getLogger(AdminStatisticsController.class);

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private PatentService patentService;

    @Autowired
    private UserStatisticsMapper userStatisticsMapper;

    /**
     * 获取用户统计数据
     * GET /api/v1/admin/statistics/users
     * 
     * 返回各类用户数量统计
     */
    @GetMapping("/users")
    public Result<Map<String, Object>> getUserStatistics() {
        logger.info("管理员查询用户统计数据");

        Map<String, Object> statistics = new HashMap<>();

        // 通过 StatisticsService 获取统计数据
        statistics.put("total", statisticsService.getTotalUsers());
        statistics.put("normalUsers", statisticsService.getNormalUserCount());
        statistics.put("enterpriseUsers", statisticsService.getTotalEnterprises());
        statistics.put("researchTeamUsers", statisticsService.getTotalResearchTeams());
        statistics.put("adminUsers", statisticsService.getAdminUserCount());

        logger.info("用户统计查询完成，总数: {}", statistics.get("total"));
        return Result.ok(statistics);
    }

    /**
     * 获取系统概览统计
     * GET /api/v1/admin/statistics/overview
     * 
     * 返回系统整体统计概览
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverviewStatistics() {
        logger.info("管理员查询系统概览统计");

        Map<String, Object> overview = new HashMap<>();

        // 用户统计（通过 StatisticsService）
        Map<String, Object> userStats = new HashMap<>();
        userStats.put("totalUsers", statisticsService.getTotalUsers());
        userStats.put("enterprises", statisticsService.getTotalEnterprises());
        userStats.put("researchTeams", statisticsService.getTotalResearchTeams());

        overview.put("users", userStats);

        // 专利统计
        Map<String, Object> patentStats = new HashMap<>();
        patentStats.put("totalPatents", patentService.getTotalCount());
        patentStats.put("boundPatents", patentService.getTotalBoundPatentCount());
        patentStats.put("unboundPatents", patentService.getTotalUnboundPatentCount());

        overview.put("patents", patentStats);

        // 其他统计
        Map<String, Object> otherStats = new HashMap<>();
        otherStats.put("totalMessages", 0);
        otherStats.put("pendingApprovals", 0);
        overview.put("others", otherStats);

        logger.info("系统概览统计查询完成");
        return Result.ok(overview);
    }

    /**
     * 获取活跃用户统计
     * GET /api/v1/admin/statistics/active-users
     * 
     * @param period 时间周期：week(7天)/month(30天)/quarter(90天)/year(365天)
     * @return 包含统计数据的Map
     */
    @GetMapping("/active-users")
    public Result<Map<String, Object>> getActiveUserStatistics(
            @RequestParam(defaultValue = "month") String period) {
        
        logger.info("管理员查询活跃用户统计，周期: {}", period);

        // 根据周期确定时间范围
        LocalDate endDate = LocalDate.now().minusDays(1);  // 不包含今日（今日数据还在统计中）
        LocalDate startDate;
        LocalDate previousStartDate;
        
        switch (period.toLowerCase()) {
            case "week":
                startDate = endDate.minusDays(6);  // 最近7天
                previousStartDate = startDate.minusDays(7);  // 前7天
                break;
            case "month":
                startDate = endDate.minusDays(29);  // 最近30天
                previousStartDate = startDate.minusDays(30);  // 前30天
                break;
            case "quarter":
                startDate = endDate.minusDays(89);  // 最近90天
                previousStartDate = startDate.minusDays(90);  // 前90天
                break;
            case "year":
                startDate = endDate.minusDays(364);  // 最近365天
                previousStartDate = startDate.minusDays(365);  // 前365天
                break;
            default:
                // 默认按月份统计
                startDate = endDate.minusDays(29);
                previousStartDate = startDate.minusDays(30);
                period = "month";
        }

        // 获取当前周期和上一周期的历史数据
        List<Map<String, Object>> currentHistory = statisticsService.getActiveUserHistory(startDate, endDate);
        List<Map<String, Object>> previousHistory = statisticsService.getActiveUserHistory(previousStartDate, startDate.minusDays(1));

        // 计算当前周期统计数据
        Map<String, Object> currentStats = calculatePeriodStatistics(currentHistory);
        
        // 计算上一周期统计数据（用于计算趋势）
        Map<String, Object> previousStats = calculatePeriodStatistics(previousHistory);

        // 计算趋势（相比上一周期）
        int currentTotal = (int) currentStats.get("totalActiveUsers");
        int previousTotal = (int) previousStats.get("totalActiveUsers");
        double trend = previousTotal > 0 ? ((double) (currentTotal - previousTotal) / previousTotal) * 100 : 0;

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("period", period);
        result.put("startDate", startDate.toString());
        result.put("endDate", endDate.toString());
        result.put("totalActiveUsers", currentTotal);
        result.put("averageDailyActiveUsers", currentStats.get("averageDailyActiveUsers"));
        result.put("peakDailyActiveUsers", currentStats.get("peakDailyActiveUsers"));
        result.put("peakDate", currentStats.get("peakDate"));
        result.put("newUsers", currentStats.get("totalNewUsers"));
        result.put("trend", Math.round(trend * 100.0) / 100.0);  // 保留2位小数
        result.put("trendDirection", trend >= 0 ? "up" : "down");
        result.put("dailyData", currentHistory);

        logger.info("活跃用户统计查询完成，周期: {}, 总活跃: {}, 趋势: {}%", period, currentTotal, 
                   Math.round(trend * 100.0) / 100.0);
        return Result.ok(result);
    }

    /**
     * 计算周期统计数据
     * 
     * @param history 历史数据列表
     * @return 统计结果Map
     */
    private Map<String, Object> calculatePeriodStatistics(List<Map<String, Object>> history) {
        Map<String, Object> stats = new HashMap<>();
        
        if (history == null || history.isEmpty()) {
            stats.put("totalActiveUsers", 0);
            stats.put("totalNewUsers", 0);
            stats.put("averageDailyActiveUsers", 0);
            stats.put("peakDailyActiveUsers", 0);
            stats.put("peakDate", null);
            return stats;
        }

        int totalActiveUsers = 0;
        int totalNewUsers = 0;
        int peakDailyActiveUsers = 0;
        String peakDate = null;

        for (Map<String, Object> dailyData : history) {
            // 累加活跃用户数
            Object activeObj = dailyData.get("activeUserCount");
            int activeUsers = 0;
            if (activeObj != null) {
                if (activeObj instanceof Integer) {
                    activeUsers = (Integer) activeObj;
                } else if (activeObj instanceof Long) {
                    activeUsers = ((Long) activeObj).intValue();
                } else if (activeObj instanceof Number) {
                    activeUsers = ((Number) activeObj).intValue();
                }
            }
            totalActiveUsers += activeUsers;

            // 累加新增用户数
            Object newUserObj = dailyData.get("newUserCount");
            int newUsers = 0;
            if (newUserObj != null) {
                if (newUserObj instanceof Integer) {
                    newUsers = (Integer) newUserObj;
                } else if (newUserObj instanceof Long) {
                    newUsers = ((Long) newUserObj).intValue();
                } else if (newUserObj instanceof Number) {
                    newUsers = ((Number) newUserObj).intValue();
                }
            }
            totalNewUsers += newUsers;

            // 记录峰值
            if (activeUsers > peakDailyActiveUsers) {
                peakDailyActiveUsers = activeUsers;
                peakDate = (String) dailyData.get("date");
            }
        }

        // 计算平均值
        double averageDaily = history.size() > 0 ? (double) totalActiveUsers / history.size() : 0;

        stats.put("totalActiveUsers", totalActiveUsers);
        stats.put("totalNewUsers", totalNewUsers);
        stats.put("averageDailyActiveUsers", Math.round(averageDaily * 100.0) / 100.0);
        stats.put("peakDailyActiveUsers", peakDailyActiveUsers);
        stats.put("peakDate", peakDate);

        return stats;
    }

    /**
     * 获取今日统计数据
     * GET /api/v1/admin/statistics/today
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayStatistics() {
        logger.info("管理员查询今日统计数据");

        Map<String, Object> todayStats = new HashMap<>();

        // 从统计聚合表查询今日数据
        LocalDate today = LocalDate.now();
        UserStatistics stats = userStatisticsMapper.selectByDate(today);

        if (stats != null) {
            todayStats.put("date", today.toString());
            todayStats.put("newUserCount", stats.getNewUserCount() != null ? stats.getNewUserCount() : 0);
            todayStats.put("activeUserCount", stats.getActiveUserCount() != null ? stats.getActiveUserCount() : 0);
            todayStats.put("newEnterpriseCount", stats.getNewEnterpriseCount() != null ? stats.getNewEnterpriseCount() : 0);
            todayStats.put("newResearchTeamCount", stats.getNewResearchTeamCount() != null ? stats.getNewResearchTeamCount() : 0);
        } else {
            // 如果聚合表没有数据，实时计算
            todayStats.put("date", today.toString());
            todayStats.put("newUserCount", statisticsService.getTodayNewUsers());
            todayStats.put("activeUserCount", statisticsService.getTodayActiveUsers());
            todayStats.put("newEnterpriseCount", 0);
            todayStats.put("newResearchTeamCount", 0);
        }

        logger.info("今日统计查询完成");
        return Result.ok(todayStats);
    }
}