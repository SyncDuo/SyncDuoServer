package com.syncduo.server.model.rclone.global;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Filter {
    @JsonProperty("DeleteExcluded")
    private boolean deleteExcluded = false;

    @JsonProperty("FilterRule")
    private List<String> filterRule = new ArrayList<>();

    @JsonProperty("FilterFrom")
    private List<String> filterFrom = new ArrayList<>();

    @JsonProperty("ExcludeRule")
    private List<String> excludeRule = new ArrayList<>();

    @JsonProperty("ExcludeFrom")
    private List<String> excludeFrom = new ArrayList<>();

    @JsonProperty("IncludeRule")
    private List<String> includeRule = new ArrayList<>();

    @JsonProperty("IncludeFrom")
    private List<String> includeFrom = new ArrayList<>();

    @JsonProperty("ExcludeFile")
    private List<String> excludeFile = new ArrayList<>();

    @JsonProperty("FilesFrom")
    private List<String> filesFrom = new ArrayList<>();

    @JsonProperty("FilesFromRaw")
    private List<String> filesFromRaw = new ArrayList<>();

    @JsonProperty("MetaRules")
    private MetaRules metaRules = new MetaRules();

    @JsonProperty("MinAge")
    // 最小值序列化时, jackson 会处理为浮点数导致超过 Long 型最小值, 所以需要加 1024
    private long minAge = Long.MIN_VALUE + 1024;

    @JsonProperty("MaxAge")
    // 最大值序列化时, jackson 会处理为浮点数导致超过 Long 型最大值, 所以需要减 1024
    private long maxAge = Long.MAX_VALUE - 1024;

    @JsonProperty("MinSize")
    private long minSize = -1;

    @JsonProperty("MaxSize")
    private long maxSize = -1;

    @JsonProperty("IgnoreCase")
    private boolean ignoreCase = false;

    @Data
    static class MetaRules {
        private List<String> filterRule = new ArrayList<>();

        private List<String> filterFrom = new ArrayList<>();

        private List<String> excludeRule = new ArrayList<>();

        private List<String> excludeFrom = new ArrayList<>();

        private List<String> includeRule = new ArrayList<>();

        private List<String> includeFrom = new ArrayList<>();
    }
}
