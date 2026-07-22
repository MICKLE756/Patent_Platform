package com.AI_Assistant.asyncMessage.message.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.AI_Assistant.asyncMessage.message.entity.MessageDelivery;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 消息投递状态Mapper接口
 */
@Mapper
public interface MessageDeliveryMapper extends BaseMapper<MessageDelivery> {

    /**
     * 根据消息ID查询投递记录
     */
    @Select("SELECT * FROM message_delivery WHERE message_id = #{messageId}")
    List<MessageDelivery> selectByMessageId(@Param("messageId") String messageId);

    /**
     * 根据状态查询投递记录
     */
    @Select("SELECT * FROM message_delivery WHERE status = #{status}")
    List<MessageDelivery> selectByStatus(@Param("status") String status);

    /**
     * 更新投递状态
     * 注意：此方法不再自动增加retry_count，重试计数由updateRetryCount方法单独处理
     */
    @Update("UPDATE message_delivery SET status = #{status}, " +
            "last_attempt_time = #{lastAttemptTime}, error_message = #{errorMessage} " +
            "WHERE id = #{id}")
    Integer updateDeliveryStatus(@Param("id") String id, @Param("status") String status,
                                 @Param("lastAttemptTime") LocalDateTime lastAttemptTime,
                                 @Param("errorMessage") String errorMessage);

    /**
     * 查询需要重试的消息（失败且重试次数未超过限制）
     * maxRetries 通过参数传入，避免与配置不一致
     */
    @Select("SELECT * FROM message_delivery WHERE status = 'failed' AND retry_count < #{maxRetries}")
    List<MessageDelivery> selectNeedRetry(@Param("maxRetries") int maxRetries);

    /**
     * 根据队列名称查询投递记录
     */
    @Select("SELECT * FROM message_delivery WHERE queue_name = #{queueName} ORDER BY create_time DESC")
    List<MessageDelivery> selectByQueueName(@Param("queueName") String queueName);

    /**
     * 根据消息ID和队列名称查询投递记录（高效查询）
     */
    @Select("SELECT * FROM message_delivery WHERE message_id = #{messageId} AND queue_name = #{queueName}")
    MessageDelivery selectByMessageIdAndQueue(@Param("messageId") String messageId, @Param("queueName") String queueName);

    /**
     * 更新重试次数
     */
    @Update("UPDATE message_delivery SET retry_count = #{retryCount}, " +
            "last_attempt_time = #{lastAttemptTime} WHERE id = #{id}")
    Integer updateRetryCount(@Param("id") String id,
                            @Param("retryCount") Integer retryCount,
                            @Param("lastAttemptTime") LocalDateTime lastAttemptTime);

    /**
     * 根据消息ID批量更新投递状态
     * 性能优化：避免循环查询
     */
    @Update("UPDATE message_delivery SET status = #{status}, " +
            "last_attempt_time = #{lastAttemptTime}, error_message = #{errorMessage} " +
            "WHERE message_id = #{messageId}")
    Integer updateDeliveryStatusByMessageId(@Param("messageId") String messageId,
                                            @Param("status") String status,
                                            @Param("lastAttemptTime") LocalDateTime lastAttemptTime,
                                            @Param("errorMessage") String errorMessage);

    /**
     * [已修复 - 问题65] 原子更新状态和重试次数
     * 在高并发场景下，同时更新状态和重试次数，避免数据不一致
     */
    @Update("UPDATE message_delivery SET status = #{status}, " +
            "retry_count = #{retryCount}, " +
            "last_attempt_time = #{lastAttemptTime}, " +
            "error_message = #{errorMessage} " +
            "WHERE id = #{id}")
    Integer updateStatusAndRetryCount(@Param("id") String id,
                                      @Param("status") String status,
                                      @Param("retryCount") Integer retryCount,
                                      @Param("lastAttemptTime") LocalDateTime lastAttemptTime,
                                      @Param("errorMessage") String errorMessage);

    /**
     * [已修复 - 问题65] 根据消息ID原子更新状态和重试次数
     * 在高并发场景下，同时更新状态和重试次数，避免数据不一致
     */
    @Update("UPDATE message_delivery SET status = #{status}, " +
            "retry_count = #{retryCount}, " +
            "last_attempt_time = #{lastAttemptTime}, " +
            "error_message = #{errorMessage} " +
            "WHERE message_id = #{messageId}")
    Integer updateStatusAndRetryCountByMessageId(@Param("messageId") String messageId,
                                                  @Param("status") String status,
                                                  @Param("retryCount") Integer retryCount,
                                                  @Param("lastAttemptTime") LocalDateTime lastAttemptTime,
                                                  @Param("errorMessage") String errorMessage);

    /**
     * 查询超时未确认的消息
     */
    @Select("SELECT * FROM message_delivery WHERE status = #{status} AND create_time < #{timeoutTime}")
    List<MessageDelivery> selectTimeoutMessages(@Param("timeoutTime") LocalDateTime timeoutTime, 
                                                @Param("status") String status);

    /**
     * 查询失败需要重试的消息
     */
    @Select("SELECT * FROM message_delivery WHERE status = 'failed' AND retry_count < #{maxRetries}")
    List<MessageDelivery> selectFailedMessages(@Param("maxRetries") int maxRetries);

    /**
     * 删除过期的投递记录
     */
    @Update("DELETE FROM message_delivery WHERE create_time < #{expireTime}")
    Integer deleteExpiredRecords(@Param("expireTime") LocalDateTime expireTime);
}