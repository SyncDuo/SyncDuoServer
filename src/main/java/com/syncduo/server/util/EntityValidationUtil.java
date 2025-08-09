package com.syncduo.server.util;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.syncflow.ChangeSyncFlowStatusRequest;
import com.syncduo.server.model.api.syncflow.CreateSyncFlowRequest;
import com.syncduo.server.model.api.syncflow.DeleteSyncFlowRequest;
import com.syncduo.server.model.api.syncflow.ManualBackupRequest;
import com.syncduo.server.model.api.syncflow.UpdateFilterCriteriaRequest;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.entity.SystemConfigEntity;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;

public class EntityValidationUtil {

    public static void isSyncFlowEntityValid(
            SyncFlowEntity syncFlowEntity,
            String onThrowingMessage) throws SyncDuoException {
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            throw new SyncDuoException(
                    onThrowingMessage,
                    new SyncDuoException("isSyncFlowEntityValid failed. syncFlowEntity is null")
            );
        }
        if (ObjectUtils.isEmpty(syncFlowEntity.getSyncFlowId())) {
            throw new SyncDuoException(
                    onThrowingMessage,
                    new SyncDuoException("isSyncFlowEntityValid failed. syncFlowId is null")
            );
        }
        if (StringUtils.isAnyBlank(
                syncFlowEntity.getSourceFolderPath(),
                syncFlowEntity.getDestFolderPath(),
                syncFlowEntity.getSyncFlowName(),
                syncFlowEntity.getSyncStatus()
        )) {
            throw new SyncDuoException(
                    onThrowingMessage,
                    new SyncDuoException("isSyncFlowEntityValid failed. " +
                            "sourceFolderPath, destFolderPath, syncFlowName or syncStatus is null." +
                            "syncFlowEntity is " + syncFlowEntity)
            );
        }
    }

    public static void isCreateSyncFlowRequestValid(
            CreateSyncFlowRequest createSyncFlowRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(createSyncFlowRequest)) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "createSyncFlowRequest is null");
        }
        // 检查 syncflow name, source folder 和 dest folder
        if (StringUtils.isAnyBlank(
                createSyncFlowRequest.getSyncFlowName(),
                createSyncFlowRequest.getSourceFolderFullPath(),
                createSyncFlowRequest.getDestFolderFullPath()
        )) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. " +
                    "syncFlowName: %s, sourceFolderPath:%s or destFolderPath:%s is null".formatted(
                            createSyncFlowRequest.getSyncFlowName(),
                            createSyncFlowRequest.getSourceFolderFullPath(),
                            createSyncFlowRequest.getDestFolderFullPath()
                    ));
        }
        // 检查过滤条件
        String filterCriteria = createSyncFlowRequest.getFilterCriteria();
        try {
            if (StringUtils.isBlank(filterCriteria)) {
                createSyncFlowRequest.setFilterCriteria(JsonUtil.serializeListToString(new ArrayList<>()));
                return;
            }
        } catch (SyncDuoException e) {
            throw new SyncDuoException("isCreateSyncFlowRequestValid failed. ", e);
        }
        try {
            JsonUtil.deserializeStringToList(filterCriteria);
        } catch (SyncDuoException e) {
            String errorMessage = "isCreateSyncFlowRequestValid failed. can't deserialize string to list. " +
                    "filterCriteria is %s".formatted(filterCriteria) + " error is " + e.getMessage();
            throw new SyncDuoException(errorMessage, e);
        }
    }

    public static void isDeleteSyncFlowRequestValid(
            DeleteSyncFlowRequest deleteSyncFlowRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(deleteSyncFlowRequest)) {
            throw new SyncDuoException("isDeleteSyncFlowRequestValid failed. deleteSyncFlowRequest is null");
        }
        if (StringUtils.isBlank(deleteSyncFlowRequest.getSyncFlowId())) {
            throw new SyncDuoException("isDeleteSyncFlowRequestValid failed. syncFlowId is null");
        }
        // 解析 syncflow id
        try {
            deleteSyncFlowRequest.setInnerSyncFlowId(Long.parseLong(deleteSyncFlowRequest.getSyncFlowId()));
        } catch (NumberFormatException e) {
            throw new SyncDuoException("isDeleteSyncFlowRequestValid failed. syncFlowId is not a number");
        }
    }

    public static void isUpdateFilterCriteriaRequestValid(
            UpdateFilterCriteriaRequest updateFilterCriteriaRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(updateFilterCriteriaRequest)) {
            throw new SyncDuoException("isUpdateFilterCriteriaRequestValid failed. " +
                    "updateFilterCriteriaRequest is null");
        }
        if (StringUtils.isAnyBlank(
                updateFilterCriteriaRequest.getSyncFlowId(),
                updateFilterCriteriaRequest.getFilterCriteria())) {
            throw new SyncDuoException("isUpdateFilterCriteriaRequestValid failed. " +
                    "syncFlowId or filterCriteria is null.");
        }
        try {
            long syncFlowId = Long.parseLong(updateFilterCriteriaRequest.getSyncFlowId());
            updateFilterCriteriaRequest.setSyncFlowIdInner(syncFlowId);
        } catch (NumberFormatException e) {
            throw new SyncDuoException("isUpdateFilterCriteriaRequestValid failed. " +
                    "syncFlowId %s can't convert to long.".formatted(updateFilterCriteriaRequest.getSyncFlowId()), e);
        }
        try {
            JsonUtil.deserializeStringToList(updateFilterCriteriaRequest.getFilterCriteria());
        } catch (SyncDuoException e) {
            throw new SyncDuoException("isUpdateFilterCriteriaRequestValid failed. " +
                    "filterCriteria %s can't convert to list<string>"
                            .formatted(updateFilterCriteriaRequest.getFilterCriteria()),
                    e);
        }
    }

    public static void isChangeSyncFlowStatusRequestValid(
            ChangeSyncFlowStatusRequest changeSyncFlowStatusRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(changeSyncFlowStatusRequest)) {
            throw new SyncDuoException("isChangeSyncFlowStatusRequestValid failed. " +
                    "changeSyncFlowStatusRequest is null");
        }
        if (StringUtils.isAnyBlank(
                changeSyncFlowStatusRequest.getSyncFlowId(),
                changeSyncFlowStatusRequest.getSyncFlowStatus()
        )) {
            throw new SyncDuoException("isChangeSyncFlowStatusRequestValid failed. " +
                    "syncFlowId or syncFlowStatus is null");
        }
        try {
            long syncFlowId = Long.parseLong(changeSyncFlowStatusRequest.getSyncFlowId());
            changeSyncFlowStatusRequest.setSyncFlowIdInner(syncFlowId);
        } catch (NumberFormatException e) {
            throw new SyncDuoException("isChangeSyncFlowStatusRequestValid failed. " +
                    "syncFlowId %s can't convert to long.".formatted(changeSyncFlowStatusRequest.getSyncFlowId()), e);
        }
        try {
            SyncFlowStatusEnum syncFlowStatusEnum =
                    SyncFlowStatusEnum.valueOf(changeSyncFlowStatusRequest.getSyncFlowStatus());
            changeSyncFlowStatusRequest.setSyncFlowStatusEnum(syncFlowStatusEnum);
        } catch (IllegalArgumentException e) {
            throw new SyncDuoException("isChangeSyncFlowStatusRequestValid failed. " +
                    "syncFlowStatus %s can't convert.".formatted(changeSyncFlowStatusRequest.getSyncFlowStatus()), e);
        }
    }

    public static void isCreateSystemConfigValid(
            SystemConfigEntity systemConfigEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(systemConfigEntity)) {
            throw new SyncDuoException("isCreateSystemConfigValid failed. systemConfigEntity is null");
        }
        if (ObjectUtils.isNotEmpty(systemConfigEntity.getSystemConfigId())) {
            throw new SyncDuoException("isCreateSystemConfigValid failed. systemConfigEntity isn't null");
        }
        checkSystemConfigEntityValue(systemConfigEntity);
    }

    public static void isUpdateSystemConfigRequestValid(
            SystemConfigEntity systemConfigEntity) throws SyncDuoException {
        if (ObjectUtils.isEmpty(systemConfigEntity)) {
            throw new SyncDuoException("isUpdateSystemConfigRequestValid failed. systemConfigEntity is null");
        }
        if (ObjectUtils.isEmpty(systemConfigEntity.getSystemConfigId())) {
            throw new SyncDuoException("isUpdateSystemConfigRequestValid failed. systemConfigId is null");
        }
        checkSystemConfigEntityValue(systemConfigEntity);
    }

    public static void checkSystemConfigEntityValue(
            SystemConfigEntity systemConfigEntity) throws SyncDuoException {
        // 不为空的 config field
        if (StringUtils.isAnyBlank(
                systemConfigEntity.getBackupStoragePath(),
                systemConfigEntity.getBackupPassword()
        )) {
            throw new SyncDuoException("checkSystemConfigEntityValue failed. " +
                    "backupStoragePath or backupPassword " +
                    "is null");
        }
        // 禁止备份间隔小于一小时
        if (systemConfigEntity.getBackupIntervalMillis() < 3600000) {
            throw new SyncDuoException("checkSystemConfigEntityValue failed. " +
                    "backupIntervalMillis is below 1 hours");
        }
    }

    public static void isManualBackupRequestValid(ManualBackupRequest manualBackupRequest) throws SyncDuoException {
        if (ObjectUtils.anyNull(manualBackupRequest)) {
            throw new SyncDuoException("isManualBackupRequestValid failed. " +
                    "manualBackupRequest is null");
        }
        if (StringUtils.isBlank(manualBackupRequest.getSyncFlowId())) {
            throw new SyncDuoException("isManualBackupRequestValid failed. " +
                    "syncFlowId is null");
        }
        // 解析 syncflow id
        try {
            manualBackupRequest.setInnerSyncFlowId(Long.parseLong(manualBackupRequest.getSyncFlowId()));
        } catch (NumberFormatException e) {
            throw new SyncDuoException("isManualBackupRequestValid failed. syncFlowId is not a number");
        }
    }
}
