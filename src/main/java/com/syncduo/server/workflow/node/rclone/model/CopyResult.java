package com.syncduo.server.workflow.node.rclone.model;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.time.OffsetDateTime;

/**
 * Rclone 日志条目
 * 对应 JSON 格式:
 * {
 *   "time": "2025-12-26T10:42:20.576592262+08:00",
 *   "level": "notice",
 *   "msg": "605.006 MiB / 605.006 MiB, 100%, 0 B/s, ETA -\n",
 *   "stats": {...},
 *   "source": "slog/logger.go:256"
 * }
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)  // 忽略未知字段
public class CopyResult {
    @JsonProperty("time")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX")
    private OffsetDateTime timestamp;

    @JsonProperty("level")
    private String level;

    @JsonProperty("msg")
    private String message;

    @JsonProperty("stats")
    private TransferStats stats;

    @JsonProperty("source")
    private String source;

    /**
     * Rclone 传输统计信息
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransferStats {
        // 基础统计
        @JsonProperty("bytes")
        private BigInteger bytes;  // 已传输字节数

        @JsonProperty("checks")
        private BigInteger checks;  // 检查的文件数

        @JsonProperty("deletedDirs")
        private BigInteger deletedDirs;  // 删除的目录数

        @JsonProperty("deletes")
        private BigInteger deletes;  // 删除的文件数

        @JsonProperty("elapsedTime")
        private Double elapsedTime = 0.0;  // 总耗时(秒)

        @JsonProperty("errors")
        private Integer errors = 0;  // 错误数

        @JsonProperty("eta")
        private Integer eta;  // 预计剩余时间(秒)，可能为null

        @JsonProperty("fatalError")
        private Boolean fatalError = false;  // 是否致命错误

        // 列表统计
        @JsonProperty("listed")
        private BigInteger listed;  // 已列出的文件数

        @JsonProperty("renames")
        private BigInteger renames;  // 重命名操作数

        @JsonProperty("retryError")
        private Boolean retryError = false;  // 是否重试错误

        // 服务器端操作统计
        @JsonProperty("serverSideCopies")
        private BigInteger serverSideCopies;  // 服务器端复制数

        @JsonProperty("serverSideCopyBytes")
        private BigInteger serverSideCopyBytes;  // 服务器端复制字节数

        @JsonProperty("serverSideMoveBytes")
        private BigInteger serverSideMoveBytes;  // 服务器端移动字节数

        @JsonProperty("serverSideMoves")
        private BigInteger serverSideMoves;  // 服务器端移动数

        // 速度与性能统计
        @JsonProperty("speed")
        private Double speed = 0.0;  // 当前速度(字节/秒)

        @JsonProperty("totalBytes")
        private BigInteger totalBytes;  // 总字节数

        @JsonProperty("totalChecks")
        private BigInteger totalChecks;  // 总检查数

        @JsonProperty("totalTransfers")
        private BigInteger totalTransfers;  // 总传输文件数

        @JsonProperty("transferTime")
        private Double transferTime = 0.0;  // 传输时间(秒)

        @JsonProperty("transfers")
        private BigInteger transfers;  // 已传输文件数
    }
}
