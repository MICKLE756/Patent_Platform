package com.AI_Assistant.backend.patent.dto;

/**
 * 专利绑定响应DTO
 */
public class PatentBindResponse {

    private boolean success;
    private String message;

    public PatentBindResponse() {}

    public PatentBindResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static PatentBindResponse success() {
        return new PatentBindResponse(true, "绑定成功");
    }

    public static PatentBindResponse failure() {
        return new PatentBindResponse(false, "绑定失败");
    }
}