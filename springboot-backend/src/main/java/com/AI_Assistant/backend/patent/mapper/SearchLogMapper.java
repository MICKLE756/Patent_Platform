package com.AI_Assistant.backend.patent.mapper;

import com.AI_Assistant.backend.patent.entity.SearchLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 搜索日志Mapper
 */
@Mapper
public interface SearchLogMapper extends BaseMapper<SearchLog> {

    /**
     * 获取热门搜索关键词（基于搜索频率统计）
     */
    List<Map<String, Object>> getHotKeywords(@Param("startTime") LocalDateTime startTime);
}
