package com.syncduo.server.exception;

public class SyncDuoException extends Exception {

    public SyncDuoException(String message) {
        super(message);
    }

    public SyncDuoException(String message, Throwable cause) {
        super(message, cause);
    }

    // Optional: Override toString to display cause more clearly (or customize the format)
    @Override
    public String toString() {
        // Optionally customize the message format, like adding "Caused by: " before the cause
        return super.toString() + (getCause() != null ? "\nCaused by: " + getCause() : "");
    }
}
