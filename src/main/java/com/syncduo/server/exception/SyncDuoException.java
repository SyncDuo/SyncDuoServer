package com.syncduo.server.exception;

public class SyncDuoException extends Exception {

    public SyncDuoException(String message) {
        super(message);
    }

    public SyncDuoException(String message, Throwable cause) {
        super(message, cause);
    }
}
