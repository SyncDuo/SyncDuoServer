package com.syncduo.server.model.rslsync.folder;

import lombok.Data;

import java.util.List;

@Data
public class FolderInfoResponse {

    // 先不对 status 检查, 即默认 status == "200"
    private int status;

    private List<FolderInfo> folders;
}
