package com.AI_Assistant.asyncMessage.message.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.AI_Assistant.asyncMessage.message.entity.UserMessage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户消息Mapper接口
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessage> {

    /**
     * 根据用户ID查询未读消息数量
     */
    @Select("SELECT COUNT(*) FROM user_message WHERE user_id = #{userId} AND status = 0")
    Integer countUnreadByUserId(@Param("userId") String userId);

    /**
     * 根据用户ID和消息类型查询未读消息数量
     */
    @Select("SELECT COUNT(*) FROM user_message WHERE user_id = #{userId} AND message_type = #{messageType} AND status = 0")
    Integer countUnreadByUserIdAndType(@Param("userId") String userId, @Param("messageType") String messageType);

    /**
     * 根据用户ID查询消息列表（按优先级和创建时间排序）
     */
    @Select("SELECT * FROM user_message WHERE user_id = #{userId} ORDER BY priority ASC, create_time DESC LIMIT #{offset}, #{limit}")
    List<UserMessage> selectByUserIdWithPagination(@Param("userId") String userId, @Param("offset") Integer offset, @Param("limit") Integer limit);

    /**
     * 根据用户ID和状态查询消息列表
     */
    @Select("SELECT * FROM user_message WHERE user_id = #{userId} AND status = #{status} ORDER BY priority ASC, create_time DESC")
    List<UserMessage> selectByUserIdAndStatus(@Param("userId") String userId, @Param("status") Integer status);

    /**
     * 根据用户ID查询未读消息（登录时拉取离线消息）
     */
    @Select("SELECT * FROM user_message WHERE user_id = #{userId} AND status = 0 ORDER BY priority ASC, create_time DESC")
    List<UserMessage> selectUnreadMessages(@Param("userId") String userId);

    /**
     * 标记消息为已读
     */
    @Update("UPDATE user_message SET status = 1 WHERE id = #{messageId}")
    Integer markAsRead(@Param("messageId") String messageId);

    /**
     * 批量标记消息为已读
     */
    @Update("UPDATE user_message SET status = 1 WHERE user_id = #{userId} AND status = 0")
    Integer markAllAsRead(@Param("userId") String userId);

    /**
     * 根据关联ID查询消息
     */
    @Select("SELECT * FROM user_message WHERE related_id = #{relatedId}")
    List<UserMessage> selectByRelatedId(@Param("relatedId") String relatedId);

    /**
     * 检查消息是否已存在（用于幂等性校验）
     */
    @Select("SELECT COUNT(*) FROM user_message WHERE id = #{messageId}")
    Integer existsById(@Param("messageId") String messageId);

    /**
     * 删除用户的所有消息
     */
    default int deleteByUserId(String userId) {
        LambdaQueryWrapper<UserMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMessage::getUserId, userId);
        return this.delete(wrapper);
    }
}