package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class BadRequestException extends BusinessException {

    public BadRequestException(String message) {
        super(StateCode.BAD_REQUEST, message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(StateCode.BAD_REQUEST, message, cause);
    }
}