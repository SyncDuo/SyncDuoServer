package com.syncduo.server.model.api.snapshots;

import lombok.Data;

import java.util.List;

@Data
public class SnapshotsResponse<T> {

    private int code;

    private String message;

    private List<T> dataList;

    private SnapshotsResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private SnapshotsResponse(int code, String message, List<T> dataList) {
        this.code = code;
        this.message = message;
        this.dataList = dataList;
    }

    public static <T> SnapshotsResponse<T> onSuccess(
            String message,
            List<T> dataList
    ) {
        return new SnapshotsResponse<>(200, message, dataList);
    }

    public static <T> SnapshotsResponse<T> onSuccess(String message) {
        return new SnapshotsResponse<>(200, message);
    }

    public static <T> SnapshotsResponse<T> onError(String message) {
        return new SnapshotsResponse<>(500, message);
    }
}
