package com.syncduo.server.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
@Getter
public enum SyncFlowStatusEnum implements Status {

    FAILED(CommonStatus.FAILED.getName()),

    RUNNING(CommonStatus.RUNNING.getName()),

    SYNC("SYNC"),

    PAUSE("PAUSE"),

    RESUME("RESUME"), // RESUME DOES NOT STORE IN DB, BUT CONVERT TO RESUME OPERATION

    RESCAN("RESCAN"); // RESCAN DOES NOT STORE IN DB, BUT CONVERT TO RESCAN OPERATION

    private final String name;

    private static final Map<SyncFlowStatusEnum, Set<SyncFlowStatusEnum>> VALID_TRANSITIONS = Map.of(
            RUNNING, Set.of(RUNNING, SYNC, PAUSE),
            SYNC, Set.of(SYNC, RUNNING, PAUSE, RESCAN),
            PAUSE, Set.of(PAUSE, RESUME)
    );

    public static boolean isTransitionValid(
            SyncFlowStatusEnum from, SyncFlowStatusEnum to) {
        if (ObjectUtils.anyNull(from, to)) {
            return false;
        }
        Set<SyncFlowStatusEnum> validNextStatus = VALID_TRANSITIONS.get(from);
        if (CollectionUtils.isEmpty(validNextStatus)) {
            return false;
        }
        return validNextStatus.contains(to);
    }

    public static List<String> getNotPauseStatus() {
        return Arrays.asList(RUNNING.name(), SYNC.name());
    }
}
