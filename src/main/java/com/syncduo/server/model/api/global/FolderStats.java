package com.syncduo.server.model.api.global;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class FolderStats {
    private String fileCount;

    private String folderCount;

    private String space; // space in MB

    public FolderStats(long fileCount, long folderCount, long space) {
        this.fileCount = String.valueOf(fileCount);
        this.folderCount = String.valueOf(folderCount);
        this.setSpace(space);
    }

    public FolderStats(long[] folderStats) {
        this.fileCount = String.valueOf(folderStats[0]);
        this.folderCount = String.valueOf(folderStats[1]);
        this.setSpace(folderStats[2]);
    }

    public void setSpace(long spaceInBytes) {
        this.space = String.format("%.2f", spaceInBytes / 1_000_000.0);
    }
}
