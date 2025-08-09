package com.syncduo.server.model.api.snapshots;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SnapshotsResponse {

    private int code;

    private String message;

    private List<SyncFlowSnapshotsInfo> syncFlowSnapshotsInfoList;

    private SnapshotsResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    private SnapshotsResponse(int code, String message, List<SyncFlowSnapshotsInfo> syncFlowSnapshotsInfoList) {
        this.code = code;
        this.message = message;
        this.syncFlowSnapshotsInfoList = syncFlowSnapshotsInfoList;
    }

    public static SnapshotsResponse onSuccess(
            String message,
            List<SyncFlowSnapshotsInfo> snapshots) {
        return new SnapshotsResponse(200, message, snapshots);
    }

    public static SnapshotsResponse onSuccess(String message) {
        return new SnapshotsResponse(200, message);
    }

    public static SnapshotsResponse onError(String message) {
        return new SnapshotsResponse(500, message);
    }
}
