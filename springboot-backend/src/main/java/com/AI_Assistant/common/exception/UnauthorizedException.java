package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(StateCode.UNAUTHORIZED, message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(StateCode.UNAUTHORIZED, message, cause);
    }
}