package com.AI_Assistant.common.util;

import java.util.List;

import com.AI_Assistant.userCenter.researchTeam.entity.ResearchTeam;
import com.AI_Assistant.userCenter.user.entity.User;

/**
 * 用户上下文工具类 - 用于存储当前请求的用户信息
 * 使用 ThreadLocal 实现请求级别数据共享
 */
public class UserContext {

    private static final ThreadLocal<User> userThreadLocal = new ThreadLocal<>();
    
    // 已绑定专利ID列表缓存（用于科研团队用户）
    private static final ThreadLocal<List<String>> boundPatentIdsThreadLocal = new ThreadLocal<>();
    
    // 科研团队信息缓存（用于科研团队用户）
    private static final ThreadLocal<ResearchTeam> researchTeamThreadLocal = new ThreadLocal<>();

    private UserContext() {
        // 私有构造函数，防止实例化
    }

    /**
     * 设置当前用户
     */
    public static void setUser(User user) {
        userThreadLocal.set(user);
    }

    /**
     * 获取当前用户
     */
    public static User getUser() {
        return userThreadLocal.get();
    }

    /**
     * 获取当前用户ID
     */
    public static String getUserId() {
        User user = userThreadLocal.get();
        return user != null ? user.getId() : null;
    }

    /**
     * 获取当前用户类型
     */
    public static Integer getUserType() {
        User user = userThreadLocal.get();
        return user != null ? user.getUserType() : null;
    }

    /**
     * 获取当前用户状态
     */
    public static Integer getUserStatus() {
        User user = userThreadLocal.get();
        return user != null ? user.getStatus() : null;
    }

    /**
     * 清除当前用户（请求结束时调用）
     */
    public static void clear() {
        userThreadLocal.remove();
        boundPatentIdsThreadLocal.remove();
        researchTeamThreadLocal.remove();
    }

    /**
     * 判断是否已登录
     */
    public static boolean isLogin() {
        return userThreadLocal.get() != null;
    }

    /**
     * 判断用户是否为企业用户
     */
    public static boolean isEnterprise() {
        return getUserType() != null && getUserType() == 1;
    }

    /**
     * 判断用户是否为科研团队用户
     */
    public static boolean isResearchTeam() {
        return getUserType() != null && getUserType() == 2;
    }

    /**
     * 判断用户是否为管理员
     */
    public static boolean isAdmin() {
        return getUserType() != null && getUserType() == 3;
    }

    // ==================== 已绑定专利ID列表相关 ====================

    /**
     * 设置当前用户已绑定的专利ID列表
     */
    public static void setBoundPatentIds(List<String> boundPatentIds) {
        boundPatentIdsThreadLocal.set(boundPatentIds);
    }

    /**
     * 获取当前用户已绑定的专利ID列表
     */
    public static List<String> getBoundPatentIds() {
        return boundPatentIdsThreadLocal.get();
    }

    // ==================== 科研团队信息相关 ====================

    /**
     * 设置当前科研团队信息
     */
    public static void setResearchTeam(ResearchTeam researchTeam) {
        researchTeamThreadLocal.set(researchTeam);
    }

    /**
     * 获取当前科研团队信息
     */
    public static ResearchTeam getResearchTeam() {
        return researchTeamThreadLocal.get();
    }
}
