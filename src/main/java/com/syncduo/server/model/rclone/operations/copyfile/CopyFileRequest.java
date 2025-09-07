package com.syncduo.server.model.rclone.operations.copyfile;

import lombok.Data;

@Data
public class CopyFileRequest {

    // 父路径
    private String srcFs;

    // 子路径
    // rclone 处理执行路径的方法: 1. 子路径包含父路径, 则执行子路径; 2. 子路径不包含父路径, 则拼接
    private String srcRemote;

    // 父路径
    private String dstFs;

    // 子路径
    private String dstRemote;

    public CopyFileRequest(
            String srcFs,
            String srcRemote,
            String dstFs,
            String dstRemote) {
        this.srcFs = srcFs;
        this.srcRemote = srcRemote;
        this.dstFs = dstFs;
        this.dstRemote = dstRemote;
    }
}
