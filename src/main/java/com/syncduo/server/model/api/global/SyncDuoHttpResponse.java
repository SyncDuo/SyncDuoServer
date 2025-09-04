package com.syncduo.server.model.api.global;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.syncduo.server.exception.SyncDuoException;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.HttpStatus;


@Data
public class SyncDuoHttpResponse<T> {

    private int statusCode;

    private String message;

    private T data;

    @JsonSerialize(using = ToStringSerializer.class)
    private final long timestamp = System.currentTimeMillis();

    private SyncDuoHttpResponse() {}

    public static <T> SyncDuoHttpResponse<T> success(T data, String message) {
        SyncDuoHttpResponse<T> result = new SyncDuoHttpResponse<>();
        result.statusCode = HttpStatus.OK.value();
        result.message = message;
        result.data = data;
        return result;
    }

    public static <T> SyncDuoHttpResponse<T> success(T data) {
        return success(data, "success");
    }

    public static SyncDuoHttpResponse<Void> success() {
        return success(null);
    }

    public static SyncDuoHttpResponse<Void> fail(SyncDuoException e) {
        SyncDuoHttpResponse<Void> result = new SyncDuoHttpResponse<>();
        // fall back 方法
        result.statusCode = ObjectUtils.isEmpty(e.getStatus()) ?
                HttpStatus.INTERNAL_SERVER_ERROR.value() :
                e.getStatus().value();
        result.message = e.getSyncDuoMessage();
        return result;
    }
}
