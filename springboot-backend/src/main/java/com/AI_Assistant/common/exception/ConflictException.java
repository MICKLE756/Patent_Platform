package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class ConflictException extends BusinessException {

    public ConflictException(String message) {
        super(StateCode.CONFLICT, message);
    }

    public ConflictException(String message, Throwable cause) {
        super(StateCode.CONFLICT, message, cause);
    }
}