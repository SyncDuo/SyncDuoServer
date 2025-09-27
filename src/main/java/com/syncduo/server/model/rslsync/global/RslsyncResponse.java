package com.syncduo.server.model.rslsync.global;

import com.syncduo.server.exception.BusinessException;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

import java.util.List;

@Data
public class RslsyncResponse<T> {

    private int httpCode;

    private boolean success;

    private String httpErrorMessage;

    private BusinessException ex;

    // 用于保持 session
    private List<String> cookie;
    
    private T data;
    
    private RslsyncResponse() {}

    // 成功响应工厂
    public static <T> RslsyncResponse<T> success(int httpCode, T data, List<String> cookie) {
        RslsyncResponse<T> rslsyncResponse = new RslsyncResponse<>();
        rslsyncResponse.setHttpCode(httpCode);
        rslsyncResponse.setSuccess(true);
        rslsyncResponse.setData(data);
        rslsyncResponse.setCookie(cookie);
        return rslsyncResponse;
    }

    // 错误响应工厂
    public static <T> RslsyncResponse<T> error(int httpCode, String errorMessage) {
        RslsyncResponse<T> rslsyncResponse = new RslsyncResponse<>();
        rslsyncResponse.setHttpCode(httpCode);
        rslsyncResponse.setSuccess(false);
        // errorMessage fall back
        rslsyncResponse.setHttpErrorMessage(StringUtils.isBlank(errorMessage) ? String.valueOf(httpCode) : errorMessage);
        return rslsyncResponse;
    }

    // 无响应工厂
    public static <T> RslsyncResponse<T> error(Throwable ex) {
        RslsyncResponse<T> rslsyncResponse = new RslsyncResponse<>();
        rslsyncResponse.setSuccess(false);
        rslsyncResponse.setEx(new BusinessException("rslsync failed with unexpected exception", ex));
        return rslsyncResponse;
    }

    public BusinessException getBusinessException() {
        if (this.success) return null;
        if (StringUtils.isNotBlank(this.httpErrorMessage)) {
            return new BusinessException("rslsync response has error http code:%s. ".formatted(httpCode) +
                    "httpErrorMessage is %s.".formatted(httpErrorMessage));
        }
        return this.ex;
    }
}
