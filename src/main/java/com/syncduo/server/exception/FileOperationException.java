package com.syncduo.server.exception;

import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;


@EqualsAndHashCode(callSuper = false)
public class FileOperationException extends SyncDuoException {

    public FileOperationException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public FileOperationException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}
