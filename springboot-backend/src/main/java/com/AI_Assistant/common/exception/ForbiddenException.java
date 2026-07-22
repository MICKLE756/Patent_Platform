package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(StateCode.FORBIDDEN, message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(StateCode.FORBIDDEN, message, cause);
    }
}