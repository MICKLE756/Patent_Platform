package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class InternalServerException extends BusinessException {

    public InternalServerException(String message) {
        super(StateCode.INTERNAL_SERVER_ERROR, message);
    }

    public InternalServerException(String message, Throwable cause) {
        super(StateCode.INTERNAL_SERVER_ERROR, message, cause);
    }
}