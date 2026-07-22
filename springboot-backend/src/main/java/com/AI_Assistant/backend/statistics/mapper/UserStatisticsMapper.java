package com.AI_Assistant.backend.statistics.mapper;

import com.AI_Assistant.backend.statistics.entity.UserStatistics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 用户统计聚合表 Mapper
 */
@Mapper
public interface UserStatisticsMapper extends BaseMapper<UserStatistics> {

    /**
     * 根据日期查询统计数据
     */
    UserStatistics selectByDate(@Param("statDate") LocalDate statDate);

    /**
     * 原子增加活跃用户数（解决并发问题）
     */
    int incrementActiveUserCount(@Param("statDate") LocalDate statDate);

    /**
     * 原子增加新增用户数（解决并发问题）
     */
    int incrementNewUserCount(@Param("statDate") LocalDate statDate);

    /**
     * 原子增加新增企业数（解决并发问题）
     */
    int incrementNewEnterpriseCount(@Param("statDate") LocalDate statDate);

    /**
     * 原子增加新增科研团队数（解决并发问题）
     */
    int incrementNewResearchTeamCount(@Param("statDate") LocalDate statDate);
}
