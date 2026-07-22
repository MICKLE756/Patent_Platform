package com.AI_Assistant.backend.patent.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.AI_Assistant.backend.patent.entity.PatentStatistics;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 专利统计数据Mapper接口
 * 统一管理专利的统计数据
 */
@Mapper
public interface PatentStatisticsMapper extends BaseMapper<PatentStatistics> {

    /**
     * 根据专利ID获取统计数据
     *
     * @param patentId 专利ID
     * @return 统计数据，包含 patent_id, view_count, search_count, click_count
     */
    @Select("SELECT patent_id, view_count, search_count, click_count " +
            "FROM patent_statistics " +
            "WHERE patent_id = #{patentId}")
    Map<String, Object> getStatisticsByPatentId(@Param("patentId") String patentId);

    /**
     * 批量获取专利统计数据
     *
     * @param patentIds 专利ID列表
     * @return 统计数据列表，包含 patent_id, view_count, search_count, click_count
     */
    @Select("<script>" +
            "SELECT patent_id, view_count, search_count, click_count " +
            "FROM patent_statistics " +
            "WHERE patent_id IN " +
            "<foreach collection='patentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</script>")
    List<Map<String, Object>> getStatisticsByPatentIds(@Param("patentIds") List<String> patentIds);

    /**
     * 增加浏览次数
     *
     * @param patentId 专利ID
     */
    @Update("UPDATE patent_statistics SET view_count = view_count + 1 WHERE patent_id = #{patentId}")
    void incrementViewCount(@Param("patentId") String patentId);

    /**
     * 增加搜索次数
     *
     * @param patentId 专利ID
     */
    @Update("UPDATE patent_statistics SET search_count = search_count + 1 WHERE patent_id = #{patentId}")
    void incrementSearchCount(@Param("patentId") String patentId);

    /**
     * 增加点击次数
     *
     * @param patentId 专利ID
     */
    @Update("UPDATE patent_statistics SET click_count = click_count + 1 WHERE patent_id = #{patentId}")
    void incrementClickCount(@Param("patentId") String patentId);

    /**
     * 初始化统计数据（专利首次被绑定时）
     * 使用 INSERT ... ON DUPLICATE KEY UPDATE 避免重复插入
     *
     * @param patentId 专利ID
     */
    @Update("INSERT INTO patent_statistics (patent_id, view_count, search_count, click_count, create_time, update_time) " +
            "VALUES (#{patentId}, 0, 0, 0, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE update_time = NOW()")
    void initStatistics(@Param("patentId") String patentId);

    /**
     * 获取热门专利统计（按浏览次数排序）
     *
     * @param limit 返回数量限制
     * @return 热门专利统计列表
     */
    @Select("SELECT patent_id, view_count, search_count, click_count " +
            "FROM patent_statistics " +
            "ORDER BY view_count DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> getHotPatentStatistics(@Param("limit") int limit);
}