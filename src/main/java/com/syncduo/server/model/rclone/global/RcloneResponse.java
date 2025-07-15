package com.syncduo.server.model.rclone.global;

import com.syncduo.server.exception.SyncDuoException;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

@Data
public class RcloneResponse<T> {

    private final int httpCode;

    private final boolean success;

    private final T data;

    // has response but http code is not 2xx
    private final ErrorInfo errorInfo;

    // does not have response
    private final Throwable ex;

    public RcloneResponse(int httpCode, boolean success, T data, ErrorInfo errorInfo, Throwable ex) {
        this.httpCode = httpCode;
        this.success = success;
        this.data = data;
        this.errorInfo = errorInfo;
        this.ex = ex;
    }

    // 成功响应工厂
    public static <T> RcloneResponse<T> success(int httpCode, T data) {
        return new RcloneResponse<>(httpCode, true, data, null, null);
    }

    // 错误响应工厂
    public static <T> RcloneResponse<T> error(int httpCode, ErrorInfo errorInfo) {
        return new RcloneResponse<>(httpCode, false, null, errorInfo, null);
    }

    // 无响应工厂
    public static <T> RcloneResponse<T> error(Throwable ex) {
        return new RcloneResponse<>(-1, false, null, null, ex);
    }

    public SyncDuoException getSyncDuoException() {
        if (this.success) {
            return null;
        }
        if (ObjectUtils.isEmpty(this.errorInfo)) {
            return new SyncDuoException("rclone has no response.", ex);
        }
        return new SyncDuoException("rclone response has error http code. " +
                "ErrorInfo is %s.".formatted(errorInfo));
    }
}
