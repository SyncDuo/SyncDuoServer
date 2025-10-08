package com.syncduo.server.model.rclone.global;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SubmitAsyncJobResponse {
    @JsonProperty("jobid")
    private int jobId;
}
