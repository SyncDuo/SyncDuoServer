package com.syncduo.server.exception;

import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;


@EqualsAndHashCode(callSuper = false)
public class BusinessException extends SyncDuoException {
    public BusinessException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public BusinessException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
