package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class UserDisabledException extends BusinessException {

    public UserDisabledException(String message) {
        super(StateCode.USER_DISABLED, message);
    }

    public UserDisabledException(String message, Throwable cause) {
        super(StateCode.USER_DISABLED, message, cause);
    }
}