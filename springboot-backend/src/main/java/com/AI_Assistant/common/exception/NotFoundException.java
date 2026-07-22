package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(StateCode.NOT_FOUND, message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(StateCode.NOT_FOUND, message, cause);
    }
}