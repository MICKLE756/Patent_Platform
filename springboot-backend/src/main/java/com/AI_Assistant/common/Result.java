package com.AI_Assistant.common;

import java.time.LocalDateTime;
import java.util.Map;

public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    /**
     * [已修复 - 问题40] 错误详情字段
     */
    private LocalDateTime timestamp;
    private String path;
    private Map<String, Object> details;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    // ==================== 成功响应 ====================

    public static Result<Void> ok() {
        return new Result<>(StateCode.OK, "success", null);
    }

    public static Result<Void> ok(String message) {
        return new Result<>(StateCode.OK, message, null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(StateCode.OK, "success", data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(StateCode.OK, message, data);
    }

    public static <T> Result<T> ok(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    // ==================== 错误响应 - 无数据 ====================

    public static Result<Void> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static Result<Void> error(String message) {
        return new Result<>(StateCode.INTERNAL_SERVER_ERROR, message, null);
    }

    // ==================== 错误响应 - 带数据 ====================

    public static <T> Result<T> error(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    public static <T> Result<T> error(String message, T data) {
        return new Result<>(StateCode.INTERNAL_SERVER_ERROR, message, data);
    }

    // ==================== 常用HTTP错误响应 ====================

    public static Result<Void> badRequest(String message) {
        return new Result<>(StateCode.BAD_REQUEST, message, null);
    }

    public static <T> Result<T> badRequest(String message, T data) {
        return new Result<>(StateCode.BAD_REQUEST, message, data);
    }

    public static Result<Void> unauthorized(String message) {
        return new Result<>(StateCode.UNAUTHORIZED, message, null);
    }

    public static <T> Result<T> unauthorized(String message, T data) {
        return new Result<>(StateCode.UNAUTHORIZED, message, data);
    }

    public static Result<Void> forbidden(String message) {
        return new Result<>(StateCode.FORBIDDEN, message, null);
    }

    public static <T> Result<T> forbidden(String message, T data) {
        return new Result<>(StateCode.FORBIDDEN, message, data);
    }

    public static Result<Void> notFound(String message) {
        return new Result<>(StateCode.NOT_FOUND, message, null);
    }

    public static <T> Result<T> notFound(String message, T data) {
        return new Result<>(StateCode.NOT_FOUND, message, data);
    }

    public static Result<Void> conflict(String message) {
        return new Result<>(StateCode.CONFLICT, message, null);
    }

    public static <T> Result<T> conflict(String message, T data) {
        return new Result<>(StateCode.CONFLICT, message, data);
    }

    public static Result<Void> tooManyRequests(String message) {
        return new Result<>(StateCode.TOO_MANY_REQUESTS, message, null);
    }

    public static <T> Result<T> tooManyRequests(String message, T data) {
        return new Result<>(StateCode.TOO_MANY_REQUESTS, message, data);
    }

    public static Result<Void> internalServerError(String message) {
        return new Result<>(StateCode.INTERNAL_SERVER_ERROR, message, null);
    }

    public static <T> Result<T> internalServerError(String message, T data) {
        return new Result<>(StateCode.INTERNAL_SERVER_ERROR, message, data);
    }

    public static Result<Void> serviceUnavailable(String message) {
        return new Result<>(StateCode.SERVICE_UNAVAILABLE, message, null);
    }

    public static <T> Result<T> serviceUnavailable(String message, T data) {
        return new Result<>(StateCode.SERVICE_UNAVAILABLE, message, data);
    }
}
