package com.syncduo.server.model.internal;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class RestoreFileCache {

    private long restoreJobId;

    private String debounceJobKey;

    private String snapshotId;

    private String folderFullPath;

    private String fileFullPath;
}
