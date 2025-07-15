package com.syncduo.server.model.rclone.core.transferred;

import lombok.Data;

import java.util.List;

@Data
public class TransferredStatResponse {
    private List<TransferStat> transferred;
}
