package com.AI_Assistant.backend.statistics.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.AI_Assistant.backend.patent.mapper.SearchLogMapper;
import com.AI_Assistant.backend.statistics.entity.UserStatistics;
import com.AI_Assistant.backend.statistics.mapper.UserStatisticsMapper;
import com.AI_Assistant.common.constant.RedisConstants;
import com.AI_Assistant.userCenter.admin.mapper.AdminMapper;
import com.AI_Assistant.userCenter.enterprise.mapper.EnterpriseMapper;
import com.AI_Assistant.userCenter.researchTeam.mapper.ResearchTeamMapper;
import com.AI_Assistant.userCenter.user.entity.User;
import com.AI_Assistant.userCenter.user.mapper.UserMapper;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统计服务 - 基于聚合表的统计实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final UserStatisticsMapper userStatisticsMapper;
    private final UserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final ResearchTeamMapper researchTeamMapper;
    private final AdminMapper adminMapper;
    private final SearchLogMapper searchLogMapper;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final com.AI_Assistant.common.util.StatisticsCacheUtil statisticsCacheUtil;
    
    private ExecutorService cacheRebuildExecutor;

    @jakarta.annotation.PostConstruct
    public void init() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "statistics-cache-rebuild-" + counter.getAndIncrement());
                t.setDaemon(false);
                return t;
            }
        };
        this.cacheRebuildExecutor = new ThreadPoolExecutor(
                2,
                2,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 获取今日新增用户数（优先从缓存读取）
     */
    public long getTodayNewUsers() {
        // 先尝试从Redis缓存获取
        try {
            Object cached = redisTemplate.opsForValue().get(RedisConstants.STATISTICS_PREFIX + "today_new_users");
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }
        } catch (Exception e) {
            log.debug("从Redis缓存获取今日新增用户数失败: {}", e.getMessage());
        }
        
        LocalDate today = LocalDate.now();
        UserStatistics stats = userStatisticsMapper.selectByDate(today);
        if (stats != null) {
            long count = stats.getNewUserCount() != null ? stats.getNewUserCount() : 0;
            // 存入缓存
            try {
                redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "today_new_users", count,
                        RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("存入Redis缓存失败: {}", e.getMessage());
            }
            return count;
        }
        // 如果没有今日统计数据，实时计算
        return calculateTodayNewUsers();
    }

    /**
     * 获取今日活跃用户数（优先从缓存读取）
     */
    public long getTodayActiveUsers() {
        // 先尝试从Redis缓存获取
        try {
            Object cached = redisTemplate.opsForValue().get(RedisConstants.STATISTICS_PREFIX + "today_active_users");
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }
        } catch (Exception e) {
            log.debug("从Redis缓存获取今日活跃用户数失败: {}", e.getMessage());
        }
        
        LocalDate today = LocalDate.now();
        UserStatistics stats = userStatisticsMapper.selectByDate(today);
        if (stats != null) {
            long count = stats.getActiveUserCount() != null ? stats.getActiveUserCount() : 0;
            // 存入缓存
            try {
                redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "today_active_users", count,
                        RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("存入Redis缓存失败: {}", e.getMessage());
            }
            return count;
        }
        // 如果没有今日统计数据，实时计算
        return calculateTodayActiveUsers();
    }

    /**
     * 获取用户总数（优先从缓存读取）
     */
    public long getTotalUsers() {
        // 先尝试从Redis缓存获取
        try {
            Object cached = redisTemplate.opsForValue().get(RedisConstants.STATISTICS_PREFIX + "total_users");
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }
        } catch (Exception e) {
            log.debug("从Redis缓存获取用户总数失败: {}", e.getMessage());
        }
        
        LocalDate today = LocalDate.now();
        UserStatistics stats = userStatisticsMapper.selectByDate(today);
        long count;
        if (stats != null && stats.getTotalUserCount() != null) {
            count = stats.getTotalUserCount();
        } else {
            count = userMapper.selectCount(null);
        }
        
        // 存入缓存
        try {
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "total_users", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("存入Redis缓存失败: {}", e.getMessage());
        }
        
        return count;
    }

    /**
     * 获取企业总数（优先从缓存读取）
     */
    public long getTotalEnterprises() {
        // 先尝试从Redis缓存获取
        try {
            Object cached = redisTemplate.opsForValue().get(RedisConstants.STATISTICS_PREFIX + "total_enterprises");
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }
        } catch (Exception e) {
            log.debug("从Redis缓存获取企业总数失败: {}", e.getMessage());
        }
        
        LocalDate today = LocalDate.now();
        UserStatistics stats = userStatisticsMapper.selectByDate(today);
        long count;
        if (stats != null && stats.getEnterpriseCount() != null) {
            count = stats.getEnterpriseCount();
        } else {
            count = enterpriseMapper.selectCount(null);
        }
        
        // 存入缓存
        try {
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "total_enterprises", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("存入Redis缓存失败: {}", e.getMessage());
        }
        
        return count;
    }

    /**
     * 获取科研团队总数（优先从缓存读取）
     */
    public long getTotalResearchTeams() {
        // 先尝试从Redis缓存获取
        try {
            Object cached = redisTemplate.opsForValue().get(RedisConstants.STATISTICS_PREFIX + "total_research_teams");
            if (cached != null) {
                return Long.parseLong(cached.toString());
            }
        } catch (Exception e) {
            log.debug("从Redis缓存获取科研团队总数失败: {}", e.getMessage());
        }
        
        LocalDate today = LocalDate.now();
        UserStatistics stats = userStatisticsMapper.selectByDate(today);
        long count;
        if (stats != null && stats.getResearchTeamCount() != null) {
            count = stats.getResearchTeamCount();
        } else {
            count = researchTeamMapper.selectCount(null);
        }
        
        // 存入缓存
        try {
            redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "total_research_teams", count,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("存入Redis缓存失败: {}", e.getMessage());
        }
        
        return count;
    }

    /**
     * 获取普通用户数（用户类型为0）
     */
    public long getNormalUserCount() {
        return userMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUserType, 0)
        );
    }

    /**
     * 获取管理员用户数（用户类型为3）
     */
    public long getAdminUserCount() {
        return userMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUserType, 3)
        );
    }

    /**
     * 获取指定日期范围的活跃用户数据
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 包含日期和活跃用户数的列表
     */
    public List<Map<String, Object>> getActiveUserHistory(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> history = new ArrayList<>();
        
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", currentDate.toString());
            
            // 从聚合表查询统计数据
            UserStatistics stats = userStatisticsMapper.selectByDate(currentDate);
            if (stats != null && stats.getActiveUserCount() != null) {
                item.put("activeUserCount", stats.getActiveUserCount());
                item.put("newUserCount", stats.getNewUserCount() != null ? stats.getNewUserCount() : 0);
            } else {
                // 如果聚合表没有数据，实时计算（主要用于今日数据）
                long activeCount = calculateActiveUsersByDate(currentDate);
                long newCount = calculateNewUsersByDate(currentDate);
                item.put("activeUserCount", (int) activeCount);
                item.put("newUserCount", (int) newCount);
            }
            
            history.add(item);
            currentDate = currentDate.plusDays(1);
        }
        
        return history;
    }

    /**
     * 获取最近N天的活跃用户数据（优先从缓存读取）
     * @param days 天数（默认7天）
     * @return 包含日期和活跃用户数的列表
     */
    public List<Map<String, Object>> getRecentActiveUserHistory(int days) {
        // 先尝试从Redis缓存获取（只缓存7天的数据）
        if (days == 7) {
            try {
                Object cached = redisTemplate.opsForValue().get(RedisConstants.STATISTICS_PREFIX + "active_user_history");
                if (cached != null) {
                    return (List<Map<String, Object>>) cached;
                }
            } catch (Exception e) {
                log.debug("从Redis缓存获取活跃用户历史数据失败: {}", e.getMessage());
            }
        }
        
        LocalDate endDate = LocalDate.now().minusDays(1);  // 不包含今日（今日数据还在统计中）
        LocalDate startDate = endDate.minusDays(days - 1);
        List<Map<String, Object>> result = getActiveUserHistory(startDate, endDate);
        
        // 如果是7天数据，存入缓存
        if (days == 7) {
            try {
                redisTemplate.opsForValue().set(RedisConstants.STATISTICS_PREFIX + "active_user_history", result,
                        RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("存入Redis缓存失败: {}", e.getMessage());
            }
        }
        
        return result;
    }

    /**
     * 记录用户登录（增加活跃用户数）
     * 使用 Redis Set 去重，确保同一用户多次登录只统计一次
     * 使用原子更新解决并发问题
     */
    @Transactional
    public void recordUserLogin(String userId) {
        if (userId == null || userId.isEmpty()) {
            log.warn("记录用户登录失败：userId为空");
            return;
        }

        LocalDate today = LocalDate.now();
        String todayStr = today.toString();

        // 生成 Redis key：statistics:active_users:2024-06-15
        String activeUsersKey = RedisConstants.STATISTICS_ACTIVE_USERS_PREFIX + todayStr;

        try {
            // 使用 Redis Set 的 SADD 命令，只有当用户ID不存在时才添加
            // SADD 返回新增的元素数量（1 = 新增成功，0 = 已存在）
            Long addedCount = redisTemplate.opsForSet().add(activeUsersKey, userId);

            // 设置过期时间（如果 key 不存在）
            redisTemplate.expire(activeUsersKey, RedisConstants.STATISTICS_ACTIVE_USERS_EXPIRE_SECONDS, TimeUnit.SECONDS);

            // addedCount == 1 表示新用户登录，需要统计
            // addedCount == 0 表示老用户今日已登录过，跳过统计
            if (addedCount != null && addedCount > 0) {
                // 新用户登录，增加活跃用户计数
                int updated = userStatisticsMapper.incrementActiveUserCount(today);

                if (updated == 0) {
                    // 记录不存在，尝试创建
                    try {
                        UserStatistics stats = new UserStatistics();
                        stats.setStatDate(today);
                        stats.setTotalUserCount(userMapper.selectCount(null).intValue());
                        stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
                        stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
                        stats.setAdminCount(adminMapper.selectCount(null).intValue());
                        stats.setNewUserCount((int) calculateTodayNewUsers());
                        stats.setActiveUserCount(1);
                        userStatisticsMapper.insert(stats);
                    } catch (Exception e) {
                        // 可能被其他线程抢先创建，再次尝试原子更新
                        log.debug("记录可能被其他线程抢先创建，尝试原子更新，日期: {}", today);
                        userStatisticsMapper.incrementActiveUserCount(today);
                    }
                }

                log.info("新用户登录已记录到统计（去重），日期: {}, userId: {}", today, userId);
                // 清除并异步重建活跃用户统计缓存，保持缓存一致性
                this.clearAndRebuildActiveUserStatisticsCache();
            } else {
                // 老用户今日已登录过，不重复统计
                log.debug("用户今日已登录过，跳过活跃用户统计，日期: {}, userId: {}", today, userId);
            }
        } catch (Exception e) {
            log.error("记录用户登录失败，日期: {}, userId: {}", today, userId, e);
            // 发生异常时，降级处理：仍然增加计数（可能重复，但保证数据不丢失）
            try {
                userStatisticsMapper.incrementActiveUserCount(today);
                // 清除并异步重建活跃用户统计缓存
                this.clearAndRebuildActiveUserStatisticsCache();
            } catch (Exception ex) {
                log.error("降级处理失败，日期: {}, userId: {}", today, userId, ex);
            }
        }
    }

    /**
     * 记录新用户注册
     * 使用原子更新解决并发问题
     */
    @Transactional
    public void recordNewUser() {
        LocalDate today = LocalDate.now();
        
        // 尝试原子更新，如果更新失败（记录不存在），则创建新记录
        int updated = userStatisticsMapper.incrementNewUserCount(today);
        
        if (updated == 0) {
            // 记录不存在，尝试创建
            try {
                UserStatistics stats = new UserStatistics();
                stats.setStatDate(today);
                stats.setTotalUserCount(userMapper.selectCount(null).intValue());
                stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
                stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
                stats.setAdminCount(adminMapper.selectCount(null).intValue());
                stats.setNewUserCount(1);
                stats.setActiveUserCount(0);
                userStatisticsMapper.insert(stats);
            } catch (Exception e) {
                // 可能被其他线程抢先创建，再次尝试原子更新
                log.debug("记录可能被其他线程抢先创建，尝试原子更新，日期: {}", today);
                userStatisticsMapper.incrementNewUserCount(today);
            }
        } else {
            // 更新所有用户相关计数
            UserStatistics stats = userStatisticsMapper.selectByDate(today);
            if (stats != null) {
                stats.setTotalUserCount(userMapper.selectCount(null).intValue());
                stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
                stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
                stats.setAdminCount(adminMapper.selectCount(null).intValue());
                userStatisticsMapper.updateById(stats);
            }
        }
        
        log.info("新用户注册已记录到统计，日期: {}", today);
        // 清除并异步重建用户统计缓存，保持缓存一致性
        this.clearAndRebuildUserStatisticsCache();
    }

    /**
     * 记录企业注册
     * 使用原子更新解决并发问题
     */
    @Transactional
    public void recordEnterprise() {
        LocalDate today = LocalDate.now();
        
        // 尝试原子更新，如果更新失败（记录不存在），则创建新记录
        int updated = userStatisticsMapper.incrementNewEnterpriseCount(today);
        
        if (updated == 0) {
            // 记录不存在，尝试创建
            try {
                UserStatistics stats = new UserStatistics();
                stats.setStatDate(today);
                stats.setTotalUserCount(userMapper.selectCount(null).intValue());
                stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
                stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
                stats.setAdminCount(adminMapper.selectCount(null).intValue());
                stats.setNewEnterpriseCount(1);
                userStatisticsMapper.insert(stats);
            } catch (Exception e) {
                // 可能被其他线程抢先创建，再次尝试原子更新
                log.debug("记录可能被其他线程抢先创建，尝试原子更新，日期: {}", today);
                userStatisticsMapper.incrementNewEnterpriseCount(today);
            }
        } else {
            // 更新所有用户相关计数
            UserStatistics stats = userStatisticsMapper.selectByDate(today);
            if (stats != null) {
                stats.setTotalUserCount(userMapper.selectCount(null).intValue());
                stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
                stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
                stats.setAdminCount(adminMapper.selectCount(null).intValue());
                userStatisticsMapper.updateById(stats);
            }
        }
        
        log.info("企业注册已记录到统计，日期: {}", today);
        // 清除并异步重建企业统计缓存，保持缓存一致性
        this.clearAndRebuildEnterpriseStatisticsCache();
    }

    /**
     * 记录科研团队注册
     * 使用原子更新解决并发问题
     */
    @Transactional
    public void recordResearchTeam() {
        LocalDate today = LocalDate.now();
        
        // 尝试原子更新，如果更新失败（记录不存在），则创建新记录
        int updated = userStatisticsMapper.incrementNewResearchTeamCount(today);
        
        if (updated == 0) {
            // 记录不存在，尝试创建
            try {
                UserStatistics stats = new UserStatistics();
                stats.setStatDate(today);
                stats.setTotalUserCount(userMapper.selectCount(null).intValue());
                stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
                stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
                stats.setAdminCount(adminMapper.selectCount(null).intValue());
                stats.setNewResearchTeamCount(1);
                userStatisticsMapper.insert(stats);
            } catch (Exception e) {
                // 可能被其他线程抢先创建，再次尝试原子更新
                log.debug("记录可能被其他线程抢先创建，尝试原子更新，日期: {}", today);
                userStatisticsMapper.incrementNewResearchTeamCount(today);
            }
        } else {
            // 更新所有用户相关计数
            UserStatistics stats = userStatisticsMapper.selectByDate(today);
            if (stats != null) {
                stats.setTotalUserCount(userMapper.selectCount(null).intValue());
                stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
                stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
                stats.setAdminCount(adminMapper.selectCount(null).intValue());
                userStatisticsMapper.updateById(stats);
            }
        }
        
        log.info("科研团队注册已记录到统计，日期: {}", today);
        // 清除并异步重建科研团队统计缓存，保持缓存一致性
        this.clearAndRebuildResearchTeamStatisticsCache();
    }

    /**
     * 定时任务：每日凌晨2点计算并保存前一天的统计数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void calculateDailyStatistics() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("开始计算每日统计数据，日期: {}", yesterday);

        UserStatistics stats = new UserStatistics();
        stats.setStatDate(yesterday);
        
        // 计算各项统计数据
        stats.setTotalUserCount(userMapper.selectCount(null).intValue());
        stats.setEnterpriseCount(enterpriseMapper.selectCount(null).intValue());
        stats.setResearchTeamCount(researchTeamMapper.selectCount(null).intValue());
        stats.setAdminCount(adminMapper.selectCount(null).intValue());
        stats.setNewUserCount((int) calculateNewUsersByDate(yesterday));
        stats.setNewEnterpriseCount((int) calculateNewEnterprisesByDate(yesterday));
        stats.setNewResearchTeamCount((int) calculateNewResearchTeamsByDate(yesterday));
        stats.setActiveUserCount((int) calculateActiveUsersByDate(yesterday));

        // 插入或更新统计记录
        UserStatistics existing = userStatisticsMapper.selectByDate(yesterday);
        if (existing != null) {
            stats.setId(existing.getId());
            userStatisticsMapper.updateById(stats);
        } else {
            userStatisticsMapper.insert(stats);
        }
        
        log.info("每日统计数据计算完成，日期: {}, 新增用户: {}, 活跃用户: {}", 
                yesterday, stats.getNewUserCount(), stats.getActiveUserCount());
    }

    /**
     * 实时计算今日新增用户数
     */
    private long calculateTodayNewUsers() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return userMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, todayStart)
                .le(User::getCreateTime, todayEnd)
        );
    }

    /**
     * 实时计算今日活跃用户数
     */
    private long calculateTodayActiveUsers() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        return userMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .ge(User::getLastLoginTime, todayStart)
                .le(User::getLastLoginTime, todayEnd)
        );
    }

    /**
     * 计算指定日期的新增用户数
     */
    private long calculateNewUsersByDate(LocalDate date) {
        LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);
        return userMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .ge(User::getCreateTime, dayStart)
                .le(User::getCreateTime, dayEnd)
        );
    }

    /**
     * 计算指定日期的活跃用户数
     */
    private long calculateActiveUsersByDate(LocalDate date) {
        LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);
        return userMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .ge(User::getLastLoginTime, dayStart)
                .le(User::getLastLoginTime, dayEnd)
        );
    }

    /**
     * 计算指定日期的新增企业数
     */
    private long calculateNewEnterprisesByDate(LocalDate date) {
        LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);
        return enterpriseMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.AI_Assistant.userCenter.enterprise.entity.Enterprise>()
                .ge(com.AI_Assistant.userCenter.enterprise.entity.Enterprise::getCreateTime, dayStart)
                .le(com.AI_Assistant.userCenter.enterprise.entity.Enterprise::getCreateTime, dayEnd)
        );
    }

    /**
     * 计算指定日期的新增科研团队数
     */
    private long calculateNewResearchTeamsByDate(LocalDate date) {
        LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);
        return researchTeamMapper.selectCount(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam>()
                .ge(com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam::getCreateTime, dayStart)
                .le(com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam::getCreateTime, dayEnd)
        );
    }

    /**
     * 获取热门搜索关键词（基于近N天搜索频率）
     *
     * @param days 天数（默认7天）
     * @return 热门关键词列表
     */
    public List<Map<String, Object>> getHotSearchKeywords(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        try {
            List<Map<String, Object>> hotKeywords = searchLogMapper.getHotKeywords(startTime);
            log.info("获取热门搜索关键词成功，天数: {}, 数量: {}", days, hotKeywords.size());
            return hotKeywords;
        } catch (Exception e) {
            log.error("获取热门搜索关键词失败，天数: {}", days, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取热门搜索关键词（带Redis缓存）
     *
     * @param days 天数（默认7天）
     * @return 热门关键词列表
     */
    public List<Map<String, Object>> getHotSearchKeywordsWithCache(int days) {
        // 先尝试从Redis缓存获取
        try {
            List<Map<String, Object>> cachedResult = (List<Map<String, Object>>) redisTemplate.opsForValue()
                    .get(RedisConstants.HOT_SEARCH_KEYWORDS_KEY);
            if (cachedResult != null && !cachedResult.isEmpty()) {
                log.info("热门搜索关键词从Redis缓存获取，数量: {}", cachedResult.size());
                return cachedResult;
            }
        } catch (Exception e) {
            log.warn("Redis缓存获取失败，将从数据库查询，错误: {}", e.getMessage());
        }

        // 从数据库查询
        List<Map<String, Object>> hotKeywords = getHotSearchKeywords(days);

        // 将结果存入Redis缓存
        try {
            redisTemplate.opsForValue().set(RedisConstants.HOT_SEARCH_KEYWORDS_KEY, hotKeywords,
                    RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS);
            log.info("热门搜索关键词已存入Redis缓存，数量: {}", hotKeywords.size());
        } catch (Exception e) {
            log.error("Redis缓存存储失败，错误: {}", e.getMessage());
        }

        return hotKeywords;
    }
    
    // ==================== 异步缓存重建方法（解决循环依赖问题） ====================
    
    /**
     * 清除并异步重建所有统计缓存
     */
    public void clearAndRebuildAllStatisticsCache() {
        try {
            statisticsCacheUtil.clearAllStatisticsCache();
            rebuildAllStatisticsCacheAsync();
            log.info("已清除并异步重建所有统计缓存");
        } catch (Exception e) {
            log.error("清除并重建统计缓存失败", e);
        }
    }
    
    /**
     * 清除并异步重建用户统计缓存
     */
    public void clearAndRebuildUserStatisticsCache() {
        try {
            statisticsCacheUtil.clearUserStatisticsCache();
            rebuildUserStatisticsCacheAsync();
            log.info("已清除并异步重建用户统计缓存");
        } catch (Exception e) {
            log.error("清除并重建用户统计缓存失败", e);
        }
    }
    
    /**
     * 清除并异步重建企业统计缓存
     */
    public void clearAndRebuildEnterpriseStatisticsCache() {
        try {
            statisticsCacheUtil.clearEnterpriseStatisticsCache();
            rebuildEnterpriseStatisticsCacheAsync();
            log.info("已清除并异步重建企业统计缓存");
        } catch (Exception e) {
            log.error("清除并重建企业统计缓存失败", e);
        }
    }
    
    /**
     * 清除并异步重建科研团队统计缓存
     */
    public void clearAndRebuildResearchTeamStatisticsCache() {
        try {
            statisticsCacheUtil.clearResearchTeamStatisticsCache();
            rebuildResearchTeamStatisticsCacheAsync();
            log.info("已清除并异步重建科研团队统计缓存");
        } catch (Exception e) {
            log.error("清除并重建科研团队统计缓存失败", e);
        }
    }
    
    /**
     * 清除并异步重建活跃用户统计缓存
     */
    public void clearAndRebuildActiveUserStatisticsCache() {
        try {
            statisticsCacheUtil.clearActiveUserStatisticsCache();
            rebuildActiveUserStatisticsCacheAsync();
            log.info("已清除并异步重建活跃用户统计缓存");
        } catch (Exception e) {
            log.error("清除并重建活跃用户统计缓存失败", e);
        }
    }
    
    /**
     * 清除并异步重建热门搜索关键词缓存
     */
    public void clearAndRebuildHotSearchKeywordsCache() {
        try {
            statisticsCacheUtil.clearHotSearchKeywordsCache();
            rebuildHotSearchKeywordsCacheAsync();
            log.info("已清除并异步重建热门搜索关键词缓存");
        } catch (Exception e) {
            log.error("清除并重建热门搜索关键词缓存失败", e);
        }
    }
    
    /**
     * 异步重建所有统计缓存
     */
    private void rebuildAllStatisticsCacheAsync() {
        CompletableFuture.runAsync(() -> {
            rebuildUserStatisticsCache();
            rebuildEnterpriseStatisticsCache();
            rebuildResearchTeamStatisticsCache();
            rebuildActiveUserStatisticsCache();
            rebuildHotSearchKeywordsCache();
        }, cacheRebuildExecutor);
    }
    
    /**
     * 异步重建用户统计缓存
     */
    private void rebuildUserStatisticsCacheAsync() {
        CompletableFuture.runAsync(() -> rebuildUserStatisticsCache(), cacheRebuildExecutor);
    }
    
    /**
     * 异步重建企业统计缓存
     */
    private void rebuildEnterpriseStatisticsCacheAsync() {
        CompletableFuture.runAsync(() -> rebuildEnterpriseStatisticsCache(), cacheRebuildExecutor);
    }
    
    /**
     * 异步重建科研团队统计缓存
     */
    private void rebuildResearchTeamStatisticsCacheAsync() {
        CompletableFuture.runAsync(() -> rebuildResearchTeamStatisticsCache(), cacheRebuildExecutor);
    }
    
    /**
     * 异步重建活跃用户统计缓存
     */
    private void rebuildActiveUserStatisticsCacheAsync() {
        CompletableFuture.runAsync(() -> rebuildActiveUserStatisticsCache(), cacheRebuildExecutor);
    }
    
    /**
     * 异步重建热门搜索关键词缓存
     */
    private void rebuildHotSearchKeywordsCacheAsync() {
        CompletableFuture.runAsync(() -> rebuildHotSearchKeywordsCache(), cacheRebuildExecutor);
    }
    
    /**
     * 重建用户统计缓存
     */
    private void rebuildUserStatisticsCache() {
        try {
            long todayNewUsers = getTodayNewUsers();
            long totalUsers = getTotalUsers();
            
            redisTemplate.opsForValue().set(
                RedisConstants.STATISTICS_PREFIX + "today_new_users", 
                todayNewUsers,
                RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, 
                TimeUnit.SECONDS
            );
            redisTemplate.opsForValue().set(
                RedisConstants.STATISTICS_PREFIX + "total_users", 
                totalUsers,
                RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, 
                TimeUnit.SECONDS
            );
            log.info("用户统计缓存重建完成: todayNewUsers={}, totalUsers={}", todayNewUsers, totalUsers);
        } catch (Exception e) {
            log.error("用户统计缓存重建失败", e);
        }
    }
    
    /**
     * 重建企业统计缓存
     */
    private void rebuildEnterpriseStatisticsCache() {
        try {
            long totalEnterprises = getTotalEnterprises();
            
            redisTemplate.opsForValue().set(
                RedisConstants.STATISTICS_PREFIX + "total_enterprises", 
                totalEnterprises,
                RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, 
                TimeUnit.SECONDS
            );
            log.info("企业统计缓存重建完成: totalEnterprises={}", totalEnterprises);
        } catch (Exception e) {
            log.error("企业统计缓存重建失败", e);
        }
    }
    
    /**
     * 重建科研团队统计缓存
     */
    private void rebuildResearchTeamStatisticsCache() {
        try {
            long totalResearchTeams = getTotalResearchTeams();
            
            redisTemplate.opsForValue().set(
                RedisConstants.STATISTICS_PREFIX + "total_research_teams", 
                totalResearchTeams,
                RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, 
                TimeUnit.SECONDS
            );
            log.info("科研团队统计缓存重建完成: totalResearchTeams={}", totalResearchTeams);
        } catch (Exception e) {
            log.error("科研团队统计缓存重建失败", e);
        }
    }
    
    /**
     * 重建活跃用户统计缓存
     */
    private void rebuildActiveUserStatisticsCache() {
        try {
            long todayActiveUsers = getTodayActiveUsers();
            
            redisTemplate.opsForValue().set(
                RedisConstants.STATISTICS_PREFIX + "today_active_users", 
                todayActiveUsers,
                RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, 
                TimeUnit.SECONDS
            );
            log.info("活跃用户统计缓存重建完成: todayActiveUsers={}", todayActiveUsers);
        } catch (Exception e) {
            log.error("活跃用户统计缓存重建失败", e);
        }
    }
    
    /**
     * 重建热门搜索关键词缓存
     */
    private void rebuildHotSearchKeywordsCache() {
        try {
            List<Map<String, Object>> hotKeywords = getHotSearchKeywords(7);
            
            redisTemplate.opsForValue().set(
                RedisConstants.HOT_SEARCH_KEYWORDS_KEY, 
                hotKeywords,
                RedisConstants.STATISTICS_CACHE_EXPIRE_SECONDS, 
                TimeUnit.SECONDS
            );
            log.info("热门搜索关键词缓存重建完成: {} 条", hotKeywords.size());
        } catch (Exception e) {
            log.error("热门搜索关键词缓存重建失败", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭统计缓存重建线程池...");
        cacheRebuildExecutor.shutdown();
        try {
            if (!cacheRebuildExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                cacheRebuildExecutor.shutdownNow();
                if (!cacheRebuildExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("统计缓存重建线程池未能正常关闭");
                }
            }
        } catch (InterruptedException e) {
            cacheRebuildExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            log.warn("统计缓存重建线程池关闭被中断");
        }
        log.info("统计缓存重建线程池已关闭");
    }
}
