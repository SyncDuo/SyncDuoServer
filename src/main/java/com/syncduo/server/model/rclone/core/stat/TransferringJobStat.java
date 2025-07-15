package com.syncduo.server.model.rclone.core.stat;

import lombok.Data;


@Data
public class TransferringJobStat {

    // total transferred bytes
    private long bytes;

    // total time for current file to transfer
    private float eta;

    // file name
    private String name;

    // progress of transferring
    private double percentage;

    // bytes per second
    private long speed;

    // speed in one second
    private double speedAvg;

    // size in files
    private long size;
}
