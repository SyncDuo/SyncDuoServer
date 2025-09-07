package com.syncduo.server.exception;

import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;


@EqualsAndHashCode(callSuper = false)
public class DbException extends SyncDuoException {

    public DbException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public DbException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
