package com.AI_Assistant.common.config;

import com.AI_Assistant.common.interceptor.LoginRequiredInterceptor;
import com.AI_Assistant.common.interceptor.PermissionInterceptor;
import com.AI_Assistant.common.interceptor.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 * 注册拦截器并配置拦截路径
 * 
 * 拦截器执行顺序（按注册顺序）：
 * 1. TokenInterceptor - 解析Token，将用户信息存入ThreadLocal
 * 2. LoginRequiredInterceptor - 验证用户是否已登录
 * 3. PermissionInterceptor - 验证管理员权限（仅管理员接口）
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private TokenInterceptor tokenInterceptor;

    @Autowired
    private LoginRequiredInterceptor loginRequiredInterceptor;

    @Autowired
    private PermissionInterceptor permissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ========== 1. Token拦截器 ==========
        // 拦截所有请求，解析Token并将用户信息存入ThreadLocal
        registry.addInterceptor(tokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",           // 登录相关接口
                        "/error",                    // 错误页面
                        "/static/**"                 // 静态资源
                );

        // ========== 2. 登录验证拦截器 ==========
        // 只拦截需要登录的接口
        registry.addInterceptor(loginRequiredInterceptor)
                .addPathPatterns(
                        "/api/v1/user/**",           // 用户相关接口
                        "/api/v1/enterpriseUsers/**",// 企业用户相关接口（修正路径）
                        "/api/v1/researchTeamUsers/**",// 科研团队用户相关接口（修正路径）
                        "/api/v1/patents/**",        // 专利相关接口
                        "/api/v1/admin/**",          // 管理员相关接口
                        "/api/v1/adminUsers/**",     // 管理员账号管理
                        "/api/v1/roles/**",          // 角色管理
                        "/api/v1/statistics/pendingPatents", // 待审核专利统计（需登录）
                        "/api/v1/statistics/userTypes",      // 用户类型统计（需登录）
                        "/api/v1/statistics/enterprises",    // 企业统计（需登录）
                        "/api/v1/statistics/researchTeams"   // 科研团队统计（需登录）
                )
                .excludePathPatterns(
                        "/api/v1/user/register",     // 注册接口不需要登录
                        "/api/v1/user/login",        // 登录接口不需要登录
                        "/api/v1/statistics/todayActiveUsers", // 公开统计接口
                        "/api/v1/statistics/todayNewUsers",    // 公开统计接口
                        "/api/v1/statistics/totalPatents",     // 公开统计接口
                        "/api/v1/statistics/userHistory",      // 公开统计接口
                        "/api/v1/statistics/recentActiveUsers",// 公开统计接口
                        "/api/v1/statistics/hotSearchKeywords",// 公开统计接口
                        "/api/v1/statistics/hotPatents",       // 公开统计接口
                        "/api/v1/statistics/activeUserHistory" // 公开统计接口
                );

        // ========== 3. 权限验证拦截器 ==========
        // 仅拦截管理员专属接口，验证角色权限
        registry.addInterceptor(permissionInterceptor)
                .addPathPatterns(
                        "/api/v1/admin/**",          // 管理员操作接口（审核、统计、配置）
                        "/api/v1/adminUsers/**",     // 管理员账号管理
                        "/api/v1/roles/**"           // 角色管理
                );
    }
}