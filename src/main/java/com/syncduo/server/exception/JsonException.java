package com.syncduo.server.exception;

import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;


@EqualsAndHashCode(callSuper = false)
public class JsonException extends SyncDuoException {

    public JsonException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public JsonException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
