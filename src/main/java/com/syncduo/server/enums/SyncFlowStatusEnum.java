package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Set;

@AllArgsConstructor
@Getter
public enum SyncFlowStatusEnum implements Status {

    FAILED(CommonStatus.FAILED.getName()),

    SYNC("SYNC"),

    PAUSE("PAUSE"),

    COPY_FILE("COPY_FILE"),

    INITIAL_SCAN("INITIAL_SCAN"),

    RESCAN("RESCAN"),

    RESUME("RESUME"),

    BACKUP_ONLY_SYNC("BACKUP_ONLY_SYNC"), // 仅备份的 syncflow status 永远是 BACKUP_ONLY_SYNC

    UNKNOWN("UNKNOWN"), // UNKNOWN DOES NOT STORE IN DB, BUT CONVERT TO BUSINESS LOGIC

    ;

    private final String name;

    private static final Map<SyncFlowStatusEnum, Set<SyncFlowStatusEnum>> VALID_TRANSITIONS = Map.of(
            FAILED, Set.of(FAILED, RESCAN, PAUSE),
            SYNC, Set.of(SYNC, PAUSE, RESCAN, FAILED, COPY_FILE),
            PAUSE, Set.of(PAUSE, RESUME),
            RESUME, Set.of(RESUME, RESCAN),
            COPY_FILE, Set.of(COPY_FILE, SYNC, PAUSE, FAILED, RESCAN, INITIAL_SCAN),
            INITIAL_SCAN, Set.of(INITIAL_SCAN, SYNC, FAILED, COPY_FILE, PAUSE),
            BACKUP_ONLY_SYNC, Set.of(BACKUP_ONLY_SYNC, PAUSE),
            RESCAN, Set.of(RESCAN, SYNC, FAILED, COPY_FILE, PAUSE)
    );

    public static boolean isTransitionProhibit(
            String name, SyncFlowStatusEnum to) {
        if (ObjectUtils.anyNull(name, to)) {
            return true;
        }
        Set<SyncFlowStatusEnum> validNextStatus = VALID_TRANSITIONS.get(fromName(name));
        if (CollectionUtils.isEmpty(validNextStatus)) {
            return true;
        }
        return !validNextStatus.contains(to);
    }

    public static boolean isBackupProhibit(String name) {
        if (StringUtils.isBlank(name)) {
            return true;
        }
        SyncFlowStatusEnum syncFlowStatusEnum = fromName(name);
        return !(syncFlowStatusEnum == SYNC || syncFlowStatusEnum == BACKUP_ONLY_SYNC);
    }

    private static SyncFlowStatusEnum fromName(String name) {
        for (SyncFlowStatusEnum value : values()) {
            if (value.getName().equals(name)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
