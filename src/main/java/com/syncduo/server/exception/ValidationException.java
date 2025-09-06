package com.syncduo.server.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;


@EqualsAndHashCode(callSuper = false)
public class ValidationException extends SyncDuoException {
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
