package com.AI_Assistant.asyncMessage.message.event;

import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;

/**
 * 消息推送监听器接口
 * 用于解耦消息消费者和WebSocket推送器
 */
public interface MessagePushListener {

    /**
     * 推送消息给指定用户
     * @param userId 用户ID
     * @param message 消息内容
     * @return 是否推送成功
     */
    boolean pushMessage(String userId, MessageDTO message);

    /**
     * 推送广播消息给所有在线用户
     * @param message 消息内容
     */
    void pushBroadcastMessage(MessageDTO message);
}