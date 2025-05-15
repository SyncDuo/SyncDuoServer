package com.syncduo.server.model.http.syncflow;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.syncduo.server.enums.SyncFlowTypeEnum;
import com.syncduo.server.enums.SyncModeEnum;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

@Data
public class RescanSyncFlowRequest {
    private String syncFlowId;
}
