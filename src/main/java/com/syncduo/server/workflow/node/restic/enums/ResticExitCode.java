package com.syncduo.server.workflow.node.restic.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResticExitCode {

    SUCCESS(0, "Command was successful"),

    COMMAND_FAILED(1, "Command failed, see command help for more details"),

    GO_RUNTIME_ERROR(2, "Go runtime error"),

    BACKUP_PARTIAL_FAILURE(3, "Backup command could not read some source data"),

    REPOSITORY_NOT_FOUND(10, "Repository does not exist"),

    FAILED_TO_LOCK_REPOSITORY(11, "Failed to lock repository"),

    WRONG_PASSWORD(12, "Wrong password"),

    INTERRUPTED(130, "Restic was interrupted using SIGINT or SIGSTOP"),

    UNKNOWN(-1, "Unknown exit code. there must be an exception"),

    ERROR_WITHOUT_EXITCODE(-2, "ERROR happened before command run"),

    ;

    private final int code;

    private final String message;

    public static ResticExitCode fromCode(int code) {
        for (ResticExitCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }

    public static ResticExitCode fromName(String name) {
        for (ResticExitCode value : values()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
