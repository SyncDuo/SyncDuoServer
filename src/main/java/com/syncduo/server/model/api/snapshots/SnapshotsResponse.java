package com.syncduo.server.model.api.snapshots;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SnapshotsResponse {

    private int code;

    private String message;

    private Map<String, List<SnapshotsInfo>> snapshots;

    private SnapshotsResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private SnapshotsResponse(int code, String message, Map<String, List<SnapshotsInfo>> snapshots) {
        this.code = code;
        this.message = message;
        this.snapshots = snapshots;
    }

    public static SnapshotsResponse onSuccess(
            String message,
            Map<String, List<SnapshotsInfo>> snapshots) {
        return new SnapshotsResponse(200, message, snapshots);
    }

    public static SnapshotsResponse onSuccess(String message) {
        return new SnapshotsResponse(200, message);
    }

    public static SnapshotsResponse onError(String message) {
        return new SnapshotsResponse(500, message);
    }
}
