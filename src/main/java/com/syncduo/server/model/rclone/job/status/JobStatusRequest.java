package com.syncduo.server.model.rclone.job.status;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class JobStatusRequest {

    @JsonProperty("jobid")
    private int jobId;
}
