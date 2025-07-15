package com.syncduo.server.model.rclone.operations.check;

import lombok.Data;

import java.util.List;

@Data
public class CheckResponse {

    private boolean success;

    private String status;

    private List<String> combined;

    private List<String> missingOnSrc;

    private List<String> missingOnDst;

    private List<String> match;

    private List<String> differ;

    private List<String> error;
}
