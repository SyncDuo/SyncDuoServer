package com.syncduo.server.model.restic.global;

import com.syncduo.server.enums.ResticExitCodeEnum;
import com.syncduo.server.exception.BusinessException;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

@Data
public class ResticExecResult<SR, FR> {

    private boolean success;

    private ResticExitCodeEnum exitCode;

    // success result
    private SR data;

    // fail result
    private FR error;

    // fall back caught exception
    private BusinessException ex;

    private ResticExecResult() {}

    // 成功响应工厂
    public static <SR, FR> ResticExecResult<SR, FR> success(ResticExitCodeEnum resticExitCodeEnum, SR data) {
        ResticExecResult<SR, FR> result = new ResticExecResult<>();
        result.setSuccess(true);
        result.setExitCode(resticExitCodeEnum);
        result.setData(data);
        return result;
    }

    // 失败响应工厂
    public static <SR, FR> ResticExecResult<SR, FR> failed(ResticExitCodeEnum resticExitCodeEnum, FR error) {
        ResticExecResult<SR, FR> result = new ResticExecResult<>();
        result.setSuccess(false);
        result.setExitCode(resticExitCodeEnum);
        result.setError(error);
        return result;
    }

    // 失败响应工厂
    public static <SR, FR> ResticExecResult<SR, FR> failed(ResticExitCodeEnum resticExitCodeEnum, Throwable ex) {
        ResticExecResult<SR, FR> result = new ResticExecResult<>();
        result.setSuccess(false);
        result.setExitCode(resticExitCodeEnum);
        result.setEx(new BusinessException("restic failed with unexpected exception.", ex));
        return result;
    }

    // Exception 响应工厂
    public static <SR, FR> ResticExecResult<SR, FR> failed(Throwable ex) {
        ResticExecResult<SR, FR> result = new ResticExecResult<>();
        result.setSuccess(false);
        result.setEx(new BusinessException("restic failed with unexpected exception.", ex));
        return result;
    }

    public BusinessException getBusinessException() {
        if (this.success) return null;
        if (ObjectUtils.isNotEmpty(error)) {
            return new BusinessException("restic command failed. " +
                    "exit code is %s. exit message is %s. ".formatted(
                            this.exitCode.getCode(), this.exitCode.getMessage()) +
                    "commandError is %s".formatted(this.error));
        }
        return this.ex;
    }
}
