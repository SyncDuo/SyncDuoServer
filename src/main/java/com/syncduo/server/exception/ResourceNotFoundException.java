package com.syncduo.server.exception;

import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;


@EqualsAndHashCode(callSuper = false)
public class ResourceNotFoundException extends SyncDuoException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message, cause);
    }
}
