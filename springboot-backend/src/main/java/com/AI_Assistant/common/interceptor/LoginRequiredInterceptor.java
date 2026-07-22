package com.AI_Assistant.common.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.AI_Assistant.common.exception.UnauthorizedException;
import com.AI_Assistant.common.util.UserContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 登录验证拦截器
 * 用于需要登录才能访问的接口
 * 检查 ThreadLocal 中是否有用户信息
 */
@Component
public class LoginRequiredInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(LoginRequiredInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!UserContext.isLogin()) {
            logger.warn("未登录用户尝试访问需要登录的接口: {}", request.getRequestURI());
            throw new UnauthorizedException("请先登录");
        }

        logger.debug("用户已登录，用户ID: {}", UserContext.getUserId());
        return true;
    }
}