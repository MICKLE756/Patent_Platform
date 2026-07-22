package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class TooManyRequestsException extends BusinessException {

    public TooManyRequestsException(String message) {
        super(StateCode.TOO_MANY_REQUESTS, message);
    }

    public TooManyRequestsException(String message, Throwable cause) {
        super(StateCode.TOO_MANY_REQUESTS, message, cause);
    }
}