package com.syncduo.server.model.rclone.job.status;

import lombok.Data;

@Data
public class JobStatusResponse {

    // in seconds
    private float duration;

    // eg: 2018-10-26T18:50:20.528746884+01:00
    private String endTime;

    private String error;

    private boolean finished;

    // eg: "job/{jobid}
    private String group;

    // same as request jobid
    private int id;

    // 使用 cmd 运行 job 的输出
    private Object output;

    // eg: 2018-10-26T18:50:20.528746884+01:00
    private String startTime;

    private boolean success;

    // 使用 _async=true 提交的 job 不会有 process
    private String progress;
}
