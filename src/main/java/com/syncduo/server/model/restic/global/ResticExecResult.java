package com.syncduo.server.model.restic.global;

import com.syncduo.server.enums.ResticExitCodeEnum;
import com.syncduo.server.exception.SyncDuoException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.List;

@Data
@NoArgsConstructor
public class ResticExecResult<T1, T2> {

    private boolean success;

    private ResticExitCodeEnum exitCode;

    // success with result
    private T1 data;

    // success with aggregate result
    private List<T1> aggData;

    // partial error
    private List<T2> aggErrors;

    // command specify error. etc. parameter failed
    private T2 commandError;

    // command exec global error
    private ExitErrors exitErrors;

    // error can't be converted to restic json response
    private SyncDuoException ex;

    // 成功响应工厂
    public static <T1, T2> ResticExecResult<T1, T2> success(
            ResticExitCodeEnum resticExitCodeEnum,
            T1 data) {
        ResticExecResult<T1, T2> result = new ResticExecResult<>();
        result.setSuccess(true);
        result.setExitCode(resticExitCodeEnum);
        result.setData(data);
        return result;
    }

    // 成功响应工厂
    public static <T1, T2> ResticExecResult<T1, T2> success(
            ResticExitCodeEnum resticExitCodeEnum,
            List<T1> aggData) {
        ResticExecResult<T1, T2> result = new ResticExecResult<>();
        result.setSuccess(true);
        result.setExitCode(resticExitCodeEnum);
        result.setAggData(aggData);
        return result;
    }

    // 部分失败响应工厂
    public static <T1, T2> ResticExecResult<T1, T2> failed(
            ResticExitCodeEnum resticExitCodeEnum,
            List<T2> partialErrors) {
        ResticExecResult<T1, T2> result = new ResticExecResult<>();
        result.setSuccess(false);
        result.setExitCode(resticExitCodeEnum);
        result.setAggErrors(partialErrors);
        return result;
    }

    // 命令特定失败响应工厂
    public static <T1, T2> ResticExecResult<T1, T2> failed(
            ResticExitCodeEnum resticExitCodeEnum,
            T2 commandError) {
        ResticExecResult<T1, T2> result = new ResticExecResult<>();
        result.setSuccess(false);
        result.setExitCode(resticExitCodeEnum);
        result.setCommandError(commandError);
        return result;
    }

    // ExitErrors响应工厂
    public static <T1, T2> ResticExecResult<T1, T2> failed(
            ExitErrors exitErrors) {
        ResticExecResult<T1, T2> result = new ResticExecResult<>();
        result.setSuccess(false);
        result.setExitCode(ResticExitCodeEnum.fromCode(exitErrors.getCode()));
        result.setExitErrors(exitErrors);
        return result;
    }

    // 其他Exception响应工厂
    public static <T1, T2> ResticExecResult<T1, T2> failed(Throwable ex) {
        ResticExecResult<T1, T2> result = new ResticExecResult<>();
        result.setSuccess(false);
        result.setEx(new SyncDuoException("restic exec failed", ex));
        return result;
    }

    public SyncDuoException getSyncDuoException() {
        if (this.success) {
            return null;
        }
        if (CollectionUtils.isNotEmpty(aggErrors)) {
            return new SyncDuoException("restic partial success. " +
                    "exit code is %s. exit message is %s. ".formatted(
                            this.exitCode.getCode(), this.exitCode.getMessage()) +
                    "partialErrors is %s".formatted(this.aggErrors));
        } else if (ObjectUtils.isNotEmpty(commandError)) {
            return new SyncDuoException("restic command failed. " +
                    "exit code is %s. exit message is %s. ".formatted(
                            this.exitCode.getCode(), this.exitCode.getMessage()) +
                    "commandError is %s".formatted(this.commandError));
        } else if (ObjectUtils.isNotEmpty(exitErrors)) {
            return new SyncDuoException("restic fatal error. " +
                    "exit code is %s. exit message is %s. ".formatted(
                            this.exitCode.getCode(), this.exitCode.getMessage()) +
                    "fatalErrors is %s".formatted(this.exitErrors));
        }
        return this.ex;
    }
}
