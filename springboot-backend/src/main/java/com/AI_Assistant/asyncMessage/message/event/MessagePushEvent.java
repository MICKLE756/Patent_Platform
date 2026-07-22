package com.AI_Assistant.asyncMessage.message.event;

import com.AI_Assistant.asyncMessage.message.dto.MessageDTO;
import org.springframework.context.ApplicationEvent;

/**
 * 消息推送事件
 * 用于解耦消息消费者和WebSocket推送器
 */
public class MessagePushEvent extends ApplicationEvent {

    private final String userId;
    private final MessageDTO message;
    private final boolean isBroadcast;

    public MessagePushEvent(Object source, String userId, MessageDTO message, boolean isBroadcast) {
        super(source);
        this.userId = userId;
        this.message = message;
        this.isBroadcast = isBroadcast;
    }

    public String getUserId() {
        return userId;
    }

    public MessageDTO getMessage() {
        return message;
    }

    public boolean isBroadcast() {
        return isBroadcast;
    }
}