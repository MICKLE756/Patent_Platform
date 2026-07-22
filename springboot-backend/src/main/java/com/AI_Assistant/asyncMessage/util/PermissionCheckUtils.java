package com.AI_Assistant.asyncMessage.util;

import com.AI_Assistant.common.exception.ForbiddenException;
import com.AI_Assistant.common.exception.UnauthorizedException;
import com.AI_Assistant.common.util.UserContext;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * 异步消息模块权限校验工具类
 * 提供公共的权限校验方法
 */
public class PermissionCheckUtils {

    /**
     * 检查用户是否已登录
     *
     * @return 登录返回用户，未登录返回null
     */
    public static User checkLogin() {
        User currentUser = UserContext.getUser();
        if (currentUser == null) {
            return null;
        }
        return currentUser;
    }

    

    /**
     * 检查用户是否为企业用户
     *
     * @param user 用户
     * @return 是企业用户返回true，否则返回false
     */
    public static boolean isEnterpriseUser(User user) {
        return user != null && user.getUserType() == 1;
    }

    /**
     * 检查用户是否为企业用户，不是返回错误结果
     *
     * @param user 用户
     * @param <T> 返回类型
     * @return 是企业用户返回null，不是返回错误结果
     */
    public static void checkEnterpriseUser(User user) {
        if (user == null) {
            throw new UnauthorizedException("用户未登录");
        }
        if (user.getUserType() != 1) {
            throw new ForbiddenException("只有企业用户可以执行此操作");
        }
    }

    /**
     * 检查资源是否属于当前用户
     *
     * @param resourceUserId 资源所属用户ID
     * @param currentUser 当前用户
     * @return 属于返回true，否则返回false
     */
    public static boolean checkResourceOwnership(String resourceUserId, User currentUser) {
        if (currentUser == null) {
            return false;
        }
        return currentUser.getId().equals(resourceUserId);
    }

    
}