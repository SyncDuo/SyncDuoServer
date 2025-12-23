package com.syncduo.server.workflow.model.api.global;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.syncduo.server.exception.SyncDuoException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;

@Data
public class FlowResponse<T> {

    private int statusCode;

    private String message;

    private T data;

    @JsonSerialize(using = ToStringSerializer.class)
    private final long timestamp = System.currentTimeMillis();

    private FlowResponse() {}

    public static <T> FlowResponse<T> success(T data, String message) {
        FlowResponse<T> result = new FlowResponse<>();
        result.statusCode = HttpStatus.OK.value();
        result.message = message;
        result.data = data;
        return result;
    }

    public static <T> FlowResponse<T> success(T data) {
        return success(data, "success");
    }

    public static FlowResponse<Void> success() {
        return success(null);
    }

    public static FlowResponse<Void> failed(String message) {
        FlowResponse<Void> result = new FlowResponse<>();
        result.statusCode = HttpStatus.BAD_REQUEST.value();
        result.message = message;
        return result;
    }

    public static FlowResponse<Void> failed(SyncDuoException e) {
        FlowResponse<Void> result = new FlowResponse<>();
        // fall back 方法
        result.statusCode = ObjectUtils.isEmpty(e.getStatus()) ?
                HttpStatus.INTERNAL_SERVER_ERROR.value() :
                e.getStatus().value();
        result.message = e.getMessageRecursive();
        return result;
    }
}
