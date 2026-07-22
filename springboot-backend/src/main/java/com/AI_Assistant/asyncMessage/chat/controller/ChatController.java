package com.AI_Assistant.asyncMessage.chat.controller;

import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.user.entity.User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    @PostMapping("/chat/results")
    public Result<Void> saveChatResult(@RequestBody(required = false) Map<String, Object> request) {
        // 验证用户登录状态
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            return Result.<Void>unauthorized("用户未登录", null);
        }
        return Result.ok("保存对话成功");
    }

    @PostMapping("/chat/contacts")
    public Result<Void> sendContacts(@RequestBody(required = false) Map<String, Object> request) {
        // 验证用户登录状态
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            return Result.<Void>unauthorized("用户未登录", null);
        }
        return Result.ok("发送联系方式成功");
    }
}
