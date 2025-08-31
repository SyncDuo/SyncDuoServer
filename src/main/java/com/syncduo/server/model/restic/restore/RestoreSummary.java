package com.syncduo.server.model.restic.restore;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigInteger;

@Data
public class RestoreSummary {

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("seconds_elapsed")
    private BigInteger secondsElapsed;

    @JsonProperty("total_files")
    private BigInteger totalFiles;

    @JsonProperty("files_restored")
    private BigInteger filesRestored;

    @JsonProperty("files_skipped")
    private BigInteger filesSkipped;

    @JsonProperty("files_deleted")
    private BigInteger filesDeleted;

    @JsonProperty("total_bytes")
    private BigInteger totalBytes;

    @JsonProperty("bytes_restored")
    private BigInteger bytesRestored;

    @JsonProperty("bytes_skipped")
    private BigInteger bytesSkipped;

    public static String getCondition() {
        return "summary";
    }
}
