package com.syncduo.server.workflow.node.model;

import com.syncduo.server.workflow.node.restic.enums.ResticExitCode;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CommandResult {
    private boolean success;
    private int exitCode; // -1 表示运行命令没有 exit code 就抛出了异常
    private String output;
    private String error;

    private CommandResult() {}

    public static CommandResult success(int exitCode, String output) {
        return new CommandResult().setSuccess(true).setExitCode(exitCode).setOutput(output);
    }

    public static CommandResult failed(int exitCode, String error) {
        return new CommandResult().setSuccess(false).setExitCode(exitCode).setError(error);
    }
}
