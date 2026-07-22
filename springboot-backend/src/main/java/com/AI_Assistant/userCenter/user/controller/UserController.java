package com.AI_Assistant.userCenter.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.dto.PageResult;
import com.AI_Assistant.userCenter.user.dto.UpdatePasswordRequest;
import com.AI_Assistant.userCenter.user.dto.UpdateUserStatusRequest;
import com.AI_Assistant.userCenter.user.dto.UserDTO;
import com.AI_Assistant.userCenter.user.service.UserService;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users/{userId}")
    public Result<UserDTO> getUser(@PathVariable String userId) {
        UserDTO userDTO = userService.getUserDTOById(userId);
        if (userDTO == null) {
            return Result.<UserDTO>error("用户不存在", null);
        }
        return Result.ok(userDTO);
    }

    /**
     * 重置用户密码（管理员操作）
     * 将用户密码重置为默认密码"123456"
     * @param userId 用户ID
     * @return 操作结果
     */
    @PostMapping("/users/{userId}/reset_password")
    public Result<Void> resetPassword(@PathVariable String userId) {
        userService.resetPassword(userId);
        return Result.ok("重置密码成功");
    }

    /**
     * 修改用户密码（用户自己操作）
     * @param userId 用户ID
     * @param request 请求体，包含 oldPassword 和 newPassword 字段
     * @return 操作结果
     */
    @PatchMapping("/users/{userId}/password")
    public Result<Void> updatePassword(@PathVariable String userId, @Validated @RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(userId, request.getOldPassword(), request.getNewPassword());
        return Result.ok("修改密码成功");
    }

    /**
     * 更新用户状态（启用/禁用）
     * @param userId 用户ID
     * @param request 请求体，包含 status 字段（0=禁用，1=启用）
     * @return 操作结果
     */
    @PatchMapping("/users/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable String userId, @Validated @RequestBody UpdateUserStatusRequest request) {
        userService.updateUserStatus(userId, request.getStatus());
        String statusText = request.getStatus() == 1 ? "启用" : "禁用";
        return Result.ok(statusText + "成功");
    }

    /**
     * 分页获取普通用户列表（userType=0）
     * @param pageNum 页码（默认1，从1开始）
     * @param pageSize 每页大小（默认10）
     * @return 分页结果
     */
    @GetMapping("/users")
    public Result<PageResult<UserDTO>> getNormalUsers(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        PageResult<UserDTO> result = userService.getNormalUsers(pageNum, pageSize);
        return Result.ok(result);
    }
}
