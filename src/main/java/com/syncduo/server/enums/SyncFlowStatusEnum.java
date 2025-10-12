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

    RUNNING(CommonStatus.RUNNING.getName()),

    SYNC("SYNC"),

    PAUSE("PAUSE"),

    RESCAN("RESCAN"),

    RESUME("RESUME"), // RESUME DOES NOT STORE IN DB, BUT CONVERT TO RESUME OPERATION

    UNKNOWN("UNKNOWN"), // UNKNOWN DOES NOT STORE IN DB, BUT CONVERT TO BUSINESS LOGIC

    ;

    private final String name;

    private static final Map<SyncFlowStatusEnum, Set<SyncFlowStatusEnum>> VALID_TRANSITIONS = Map.of(
            FAILED, Set.of(RESCAN),
            RUNNING, Set.of(RUNNING, SYNC, PAUSE, FAILED),
            SYNC, Set.of(SYNC, RUNNING, PAUSE, RESCAN, FAILED),
            PAUSE, Set.of(PAUSE, RESUME),
            RESCAN, Set.of(SYNC, FAILED, PAUSE)
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
