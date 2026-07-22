package com.AI_Assistant.common.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.AI_Assistant.common.Result;
import com.AI_Assistant.common.StateCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "com.AI_Assistant")
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * [已修复 - 问题35] 环境标识，用于区分开发环境和生产环境
     * 生产环境不返回详细错误信息，避免信息泄露
     */
    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletResponse response, HttpServletRequest request) {
        logger.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus httpStatus = mapCodeToHttpStatus(e.getCode());
        response.setStatus(httpStatus.value());
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(e.getCode(), e.getMessage(), null);
        result.setPath(request.getRequestURI());
        return result;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        logger.warn("参数校验失败: {}", message);
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.BAD_REQUEST, message, null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34] 添加数据访问异常处理
     * 数据库连接失败、SQL执行错误等
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleDataAccessException(DataAccessException e, HttpServletRequest request) {
        logger.error("数据访问异常", e);
        String message = isProduction() ? "数据库服务暂时不可用，请稍后重试" : e.getMessage();
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.SERVICE_UNAVAILABLE, message, null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34] 添加HTTP方法不支持异常处理
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logger.warn("HTTP方法不支持: {}", e.getMethod());
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.METHOD_NOT_ALLOWED, "请求方法不支持", null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34] 添加媒体类型不支持异常处理
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public Result<Void> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        logger.warn("媒体类型不支持: {}", e.getContentType());
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.INTERNAL_SERVER_ERROR, "请求格式不支持，请使用application/json", null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34] 添加空指针异常处理
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleNullPointerException(NullPointerException e, HttpServletRequest request) {
        logger.error("空指针异常，请求路径: {}", request.getRequestURI(), e);
        String message = isProduction() ? "服务器内部错误" : "空指针异常: " + e.getMessage();
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.INTERNAL_SERVER_ERROR, message, null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34] 添加非法参数异常处理（更细粒度）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        logger.warn("参数校验失败: {}", e.getMessage());
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.BAD_REQUEST, e.getMessage(), null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34] 添加非法状态异常处理（更细粒度）
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
        logger.warn("非法状态异常: {}", e.getMessage());
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.CONFLICT, e.getMessage(), null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34][已修复 - 问题35] 修改运行时异常处理
     * 区分开发环境和生产环境，生产环境不返回详细错误信息
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        logger.error("运行时异常，请求路径: {}", request.getRequestURI(), e);
        String message = isProduction() ? "服务器内部错误" : e.getMessage();
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.INTERNAL_SERVER_ERROR, message, null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * [已修复 - 问题34][已修复 - 问题35] 修改未捕获异常处理
     * 区分开发环境和生产环境，生产环境不返回详细错误信息
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        logger.error("未捕获的异常，请求路径: {}", request.getRequestURI(), e);
        String message = isProduction() ? "服务器内部错误" : e.getMessage();
        // [已修复 - 问题40] 设置错误详情字段
        Result<Void> result = Result.error(StateCode.INTERNAL_SERVER_ERROR, message, null);
        result.setPath(request.getRequestURI());
        return result;
    }

    /**
     * 判断是否为生产环境
     */
    private boolean isProduction() {
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }

    private HttpStatus mapCodeToHttpStatus(int code) {
        // [已修复 - 问题37] 将精确匹配放在范围匹配之前，避免被遮蔽
        if (code == 4001) {
            return HttpStatus.UNAUTHORIZED;
        } else if (code == 4002 || code == 6007 || code == 6009) {
            return HttpStatus.FORBIDDEN;
        } else if (code == 4003) {
            return HttpStatus.NOT_FOUND;
        } else if (code == 4004) {
            return HttpStatus.METHOD_NOT_ALLOWED;
        } else if (code == 4005) {
            return HttpStatus.CONFLICT;
        } else if (code == 4006) {
            return HttpStatus.TOO_MANY_REQUESTS;
        } else if (code >= 4000 && code < 4003) {
            return HttpStatus.BAD_REQUEST;
        } else if (code >= 5000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}