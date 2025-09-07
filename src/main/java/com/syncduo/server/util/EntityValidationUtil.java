package com.syncduo.server.util;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.api.syncflow.*;
import com.syncduo.server.model.entity.SyncFlowEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class EntityValidationUtil {

    public static void isSyncFlowEntityValid(
            SyncFlowEntity syncFlowEntity) throws ValidationException {
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            throw new ValidationException("isSyncFlowEntityValid failed. syncFlowEntity is null");
        }
        if (ObjectUtils.isEmpty(syncFlowEntity.getSyncFlowId())) {
            throw new ValidationException("isSyncFlowEntityValid failed. syncFlowId is null");
        }
        if (StringUtils.isAnyBlank(
                syncFlowEntity.getSourceFolderPath(),
                syncFlowEntity.getDestFolderPath(),
                syncFlowEntity.getSyncFlowName(),
                syncFlowEntity.getSyncStatus()
        )) {
            throw new ValidationException("isSyncFlowEntityValid failed. " +
                            "sourceFolderPath, destFolderPath, syncFlowName or syncStatus is null." +
                            "syncFlowEntity is %s".formatted(syncFlowEntity));
        }
    }

    public static void isCreateSyncFlowRequestValid(
            CreateSyncFlowRequest createSyncFlowRequest) throws ValidationException {
        if (ObjectUtils.isEmpty(createSyncFlowRequest)) {
            throw new ValidationException("isCreateSyncFlowRequestValid failed. " +
                    "createSyncFlowRequest is null");
        }
        // 检查 syncflow name, source folder 和 dest folder
        if (StringUtils.isAnyBlank(
                createSyncFlowRequest.getSyncFlowName(),
                createSyncFlowRequest.getSourceFolderFullPath(),
                createSyncFlowRequest.getDestFolderFullPath()
        )) {
            throw new ValidationException("isCreateSyncFlowRequestValid failed. " +
                    "syncFlowName: %s, sourceFolderPath:%s or destFolderPath:%s is null".formatted(
                            createSyncFlowRequest.getSyncFlowName(),
                            createSyncFlowRequest.getSourceFolderFullPath(),
                            createSyncFlowRequest.getDestFolderFullPath()));
        }
        // 检查过滤条件
        String filterCriteria = createSyncFlowRequest.getFilterCriteria();
        try {
            if (StringUtils.isBlank(filterCriteria)) {
                createSyncFlowRequest.setFilterCriteria(JsonUtil.serializeListToString(new ArrayList<>()));
            } else {
                JsonUtil.deserializeStringToList(filterCriteria);
            }
        } catch (SyncDuoException e) {
            throw new ValidationException("isCreateSyncFlowRequestValid failed. " +
                    "filterCriteria:%s can't deserialize to list<String>".formatted(filterCriteria));
        }
    }

    public static void isDeleteSyncFlowRequestValid(
            DeleteSyncFlowRequest deleteSyncFlowRequest) throws ValidationException {
        if (ObjectUtils.isEmpty(deleteSyncFlowRequest)) {
            throw new ValidationException("isDeleteSyncFlowRequestValid failed. deleteSyncFlowRequest is null");
        }
        if (StringUtils.isBlank(deleteSyncFlowRequest.getSyncFlowId())) {
            throw new ValidationException("isDeleteSyncFlowRequestValid failed. syncFlowId is null");
        }
        // 解析 syncflow id
        try {
            deleteSyncFlowRequest.setInnerSyncFlowId(Long.parseLong(deleteSyncFlowRequest.getSyncFlowId()));
        } catch (NumberFormatException e) {
            throw new ValidationException("isDeleteSyncFlowRequestValid failed. syncFlowId is not a number");
        }
    }

    public static void isUpdateFilterCriteriaRequestValid(
            UpdateFilterCriteriaRequest updateFilterCriteriaRequest) throws ValidationException {
        if (ObjectUtils.isEmpty(updateFilterCriteriaRequest)) {
            throw new ValidationException("isUpdateFilterCriteriaRequestValid failed. " +
                    "updateFilterCriteriaRequest is null");
        }
        if (StringUtils.isAnyBlank(
                updateFilterCriteriaRequest.getSyncFlowId(),
                updateFilterCriteriaRequest.getFilterCriteria())) {
            throw new ValidationException("isUpdateFilterCriteriaRequestValid failed. " +
                    "syncFlowId or filterCriteria is null.");
        }
        try {
            long syncFlowId = Long.parseLong(updateFilterCriteriaRequest.getSyncFlowId());
            updateFilterCriteriaRequest.setSyncFlowIdInner(syncFlowId);
        } catch (NumberFormatException e) {
            throw new ValidationException("isUpdateFilterCriteriaRequestValid failed. " +
                    "syncFlowId %s can't convert to long.".formatted(updateFilterCriteriaRequest.getSyncFlowId()));
        }
        try {
            JsonUtil.deserializeStringToList(updateFilterCriteriaRequest.getFilterCriteria());
        } catch (SyncDuoException e) {
            throw new ValidationException("isUpdateFilterCriteriaRequestValid failed. " +
                    "filterCriteria %s can't convert to list<string>"
                            .formatted(updateFilterCriteriaRequest.getFilterCriteria()));
        }
    }

    public static void isChangeSyncFlowStatusRequestValid(
            ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) throws ValidationException {
        if (ObjectUtils.isEmpty(changeSyncFlowStatusRequest)) {
            throw new ValidationException("isChangeSyncFlowStatusRequestValid failed. " +
                    "changeSyncFlowStatusRequest is null");
        }
        String syncFlowStatus = changeSyncFlowStatusRequest.getSyncFlowStatus();
        if (StringUtils.isAnyBlank(
                changeSyncFlowStatusRequest.getSyncFlowId(),
                syncFlowStatus
        )) {
            throw new ValidationException("isChangeSyncFlowStatusRequestValid failed. " +
                    "syncFlowId or syncFlowStatus is null");
        }
        try {
            long syncFlowId = Long.parseLong(changeSyncFlowStatusRequest.getSyncFlowId());
            changeSyncFlowStatusRequest.setSyncFlowIdInner(syncFlowId);
        } catch (NumberFormatException e) {
            throw new ValidationException("isChangeSyncFlowStatusRequestValid failed. " +
                    "syncFlowId %s can't convert to long.".formatted(changeSyncFlowStatusRequest.getSyncFlowId()));
        }
        try {
            SyncFlowStatusEnum syncFlowStatusEnum = SyncFlowStatusEnum.valueOf(syncFlowStatus);
            changeSyncFlowStatusRequest.setSyncFlowStatusEnum(syncFlowStatusEnum);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("isChangeSyncFlowStatusRequestValid failed. " +
                    "syncFlowStatus %s can't convert to enum.".formatted(syncFlowStatus));
        }
    }

    public static void isManualBackupRequestValid(ManualBackupRequest manualBackupRequest) throws ValidationException {
        if (ObjectUtils.anyNull(manualBackupRequest)) {
            throw new ValidationException("isManualBackupRequestValid failed. " +
                    "manualBackupRequest is null");
        }
        if (StringUtils.isBlank(manualBackupRequest.getSyncFlowId())) {
            throw new ValidationException("isManualBackupRequestValid failed. " +
                    "syncFlowId is null");
        }
        // 解析 syncflow id
        try {
            manualBackupRequest.setInnerSyncFlowId(Long.parseLong(manualBackupRequest.getSyncFlowId()));
        } catch (NumberFormatException e) {
            throw new ValidationException("isManualBackupRequestValid failed. syncFlowId is not a number");
        }
    }

    public static void isSnapshotFileInfoListValid(
            List<SnapshotFileInfo> snapshotFileInfoList) throws ValidationException {
        if (CollectionUtils.isEmpty(snapshotFileInfoList)) {
            throw new ValidationException("isSnapshotFileInfoListValid failed. snapshotFileInfoList is null");
        }
        for (SnapshotFileInfo snapshotFileInfo : snapshotFileInfoList) {
            if (ObjectUtils.isEmpty(snapshotFileInfo)) {
                throw new ValidationException("isSnapshotFileInfoListValid failed. snapshotFileInfo is null");
            }
            if (ObjectUtils.anyNull(
                    snapshotFileInfo.getSnapshotId(),
                    snapshotFileInfo.getFileName(),
                    snapshotFileInfo.getPath()
            )) {
                throw new ValidationException("isSnapshotFileInfoListValid failed. " +
                        "snapshotId, fileName or path is null");
            }
            if (StringUtils.isBlank(snapshotFileInfo.getType())) {
                throw new ValidationException("isSnapshotFileInfoListValid failed. type is null");
            }
        }
    }
}
