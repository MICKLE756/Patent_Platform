package com.AI_Assistant.common.exception;

import com.AI_Assistant.common.StateCode;

public class PermissionDeniedException extends BusinessException {

    public PermissionDeniedException(String message) {
        super(StateCode.PERMISSION_DENIED, message);
    }

    public PermissionDeniedException(String message, Throwable cause) {
        super(StateCode.PERMISSION_DENIED, message, cause);
    }
}