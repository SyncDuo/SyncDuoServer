package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Map;
import java.util.Set;

@AllArgsConstructor
@Getter
public enum SyncFlowStatusEnum implements Status {

    FAILED(CommonStatus.FAILED.getName()),

    SYNC("SYNC"),

    PAUSE("PAUSE"),

    COPY_FILE("COPY_FILE"),

    INITIAL_SCAN("INIT_SCAN"),

    RESCAN("RESCAN"),

    RESUME("RESUME"), // RESUME DOES NOT STORE IN DB, BUT CONVERT TO BUSINESS LOGIC

    UNKNOWN("UNKNOWN"), // UNKNOWN DOES NOT STORE IN DB, BUT CONVERT TO BUSINESS LOGIC

    ;

    private final String name;

    private static final Map<SyncFlowStatusEnum, Set<SyncFlowStatusEnum>> VALID_TRANSITIONS = Map.of(
            FAILED, Set.of(FAILED, RESCAN),
            SYNC, Set.of(SYNC, PAUSE, RESCAN, FAILED, COPY_FILE),
            PAUSE, Set.of(PAUSE, RESUME),
            COPY_FILE, Set.of(COPY_FILE, SYNC, PAUSE, FAILED, RESCAN, INITIAL_SCAN),
            INITIAL_SCAN, Set.of(INITIAL_SCAN, SYNC, FAILED, COPY_FILE, PAUSE),
            RESCAN, Set.of(RESCAN, SYNC, FAILED, COPY_FILE, PAUSE),
            RESUME, Set.of(RESUME, RESCAN)
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

    private static SyncFlowStatusEnum fromName(String name) {
        for (SyncFlowStatusEnum value : values()) {
            if (value.getName().equals(name)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
