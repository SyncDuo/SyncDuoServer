package com.syncduo.server.workflow.model.api.snapshot;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotItemDTO {
    private String name;

    private String type;

    private String path;

    private Long size; // bytes

    private Instant ctime;
}
