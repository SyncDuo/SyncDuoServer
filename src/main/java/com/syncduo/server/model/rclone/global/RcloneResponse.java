package com.syncduo.server.model.rclone.global;

import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.BackupJobEntity;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

@Data
public class RcloneResponse<T> {

    private int httpCode;

    private boolean success;

    private T data;

    // has response but http code is not 2xx
    private ErrorInfo errorInfo;

    // does not have response
    private BusinessException ex;

    private RcloneResponse() {}

    // 成功响应工厂
    public static <T> RcloneResponse<T> success(int httpCode, T data) {
        RcloneResponse<T> rcloneResponse = new RcloneResponse<>();
        rcloneResponse.setHttpCode(httpCode);
        rcloneResponse.setSuccess(true);
        rcloneResponse.setData(data);
        return rcloneResponse;
    }

    // 错误响应工厂
    public static <T> RcloneResponse<T> error(int httpCode, ErrorInfo errorInfo) {
        RcloneResponse<T> rcloneResponse = new RcloneResponse<>();
        rcloneResponse.setHttpCode(httpCode);
        rcloneResponse.setSuccess(false);
        rcloneResponse.setErrorInfo(errorInfo);
        return rcloneResponse;
    }

    // 无响应工厂
    public static <T> RcloneResponse<T> error(Throwable ex) {
        RcloneResponse<T> rcloneResponse = new RcloneResponse<>();
        rcloneResponse.setSuccess(false);
        rcloneResponse.setEx(new BusinessException("rclone failed with unexpected exception", ex));
        return rcloneResponse;
    }

    public BusinessException getBusinessException() {
        if (this.success) return null;
        if (ObjectUtils.isNotEmpty(this.errorInfo)) {
            return new BusinessException("rclone response has error http code. " +
                    "ErrorInfo is %s.".formatted(errorInfo));
        }
        return this.ex;
    }
}
