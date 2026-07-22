package com.AI_Assistant.asyncMessage.message.mapper;

import com.AI_Assistant.asyncMessage.message.entity.BroadcastMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 广播消息Mapper接口
 */
@Mapper
public interface BroadcastMessageMapper extends BaseMapper<BroadcastMessage> {

    /**
     * 查询最近的广播消息（按优先级和创建时间排序）
     */
    @Select("SELECT * FROM broadcast_message ORDER BY priority ASC, create_time DESC LIMIT #{limit}")
    List<BroadcastMessage> selectRecentMessages(@Param("limit") Integer limit);

    /**
     * 查询指定时间之后的广播消息（按优先级和创建时间排序）
     */
    @Select("SELECT * FROM broadcast_message WHERE create_time > #{since} ORDER BY priority ASC, create_time DESC")
    List<BroadcastMessage> selectMessagesSince(@Param("since") LocalDateTime since);

    /**
     * 分页查询广播消息
     */
    @Select("SELECT * FROM broadcast_message ORDER BY priority ASC, create_time DESC LIMIT #{offset}, #{limit}")
    List<BroadcastMessage> selectWithPagination(@Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 检查广播消息是否已存在（用于幂等性校验）
     */
    @Select("SELECT COUNT(*) FROM broadcast_message WHERE id = #{messageId}")
    Integer existsById(@Param("messageId") String messageId);
}
