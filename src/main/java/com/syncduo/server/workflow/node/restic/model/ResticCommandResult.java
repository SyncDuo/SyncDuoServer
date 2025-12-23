package com.syncduo.server.workflow.node.restic.model;

import com.syncduo.server.workflow.node.restic.enums.ResticExitCode;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ResticCommandResult {

    private final boolean success;
    private final ResticExitCode resticExitCode;
    private String jsonOutput;
    private String errorOutput;

    private ResticCommandResult(boolean success, ResticExitCode resticExitCode) {
        this.success = success;
        this.resticExitCode = resticExitCode;
    }

    public static ResticCommandResult success(ResticExitCode resticExitCode, String jsonOutput) {
        return new ResticCommandResult(true, resticExitCode).setJsonOutput(jsonOutput);
    }

    public static ResticCommandResult failed(ResticExitCode resticExitCode, String errorOutput) {
        return new ResticCommandResult(false, resticExitCode).setErrorOutput(errorOutput);
    }
}
