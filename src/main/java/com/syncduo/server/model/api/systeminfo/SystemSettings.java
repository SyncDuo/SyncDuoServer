package com.syncduo.server.model.api.systeminfo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("syncduo.server")
@Component
@Data
@NoArgsConstructor
public class SystemSettings {

    private System system;

    private Rclone rclone;

    private Restic restic;

    @Data
    public static class System {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long folderWatcherIntervalMillis;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long checkSyncflowStatusIntervalMillis;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long backupIntervalMillis;
    }

    @Data
    public static class Rclone {
        private String httpBaseUrl;

        private String logFolderPath;
    }

    @Data
    public static class Restic {
        private String backupPath;

        private String restorePath;

        @JsonSerialize(using = ToStringSerializer.class)
        private Long restoreAgeSec;
    }
}
