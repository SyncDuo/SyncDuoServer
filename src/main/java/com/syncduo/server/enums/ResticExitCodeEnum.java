package com.syncduo.server.enums;

import lombok.Getter;

@Getter
public enum ResticExitCodeEnum {

    SUCCESS(0, "Command was successful"),

    COMMAND_FAILED(1, "Command failed, see command help for more details"),

    GO_RUNTIME_ERROR(2, "Go runtime error"),

    BACKUP_PARTIAL_FAILURE(3, "Backup command could not read some source data"),

    REPOSITORY_NOT_FOUND(10, "Repository does not exist"),

    FAILED_TO_LOCK_REPOSITORY(11, "Failed to lock repository"),

    WRONG_PASSWORD(12, "Wrong password"),

    INTERRUPTED(130, "Restic was interrupted using SIGINT or SIGSTOP"),

    UNKNOWN(-1, "Unknown exit code");

    private final int code;

    private final String message;

    ResticExitCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ResticExitCodeEnum fromCode(int code) {
        for (ResticExitCodeEnum value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
