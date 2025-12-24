package com.syncduo.server.workflow.node.restic.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.syncduo.server.model.restic.snapshots.SnapshotSummary;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class Snapshot {

    @JsonProperty("time")
    private OffsetDateTime time;

    @JsonProperty("parent")
    private String parent;

    @JsonProperty("tree")
    private String tree;

    @JsonProperty("paths")
    private List<String> paths;

    @JsonProperty("hostname")
    private String hostname;

    @JsonProperty("username")
    private String username;

    @JsonProperty("uid")
    private Long uid;

    @JsonProperty("gid")
    private Long gid;

    @JsonProperty("excludes")
    private List<String> excludes;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("program_version")
    private String programVersion;

    @JsonProperty("summary")
    private SnapshotSummary summary;

    @JsonProperty("id")
    private String id;

    @Deprecated
    @JsonProperty("short_id")
    private String shortId;
}
