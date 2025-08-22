package com.syncduo.server.controller;

import com.syncduo.server.enums.ResticNodeTypeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.api.snapshots.SnapshotInfo;
import com.syncduo.server.model.api.snapshots.SnapshotsResponse;
import com.syncduo.server.model.api.snapshots.SyncFlowWithSnapshots;
import com.syncduo.server.model.api.syncflow.ManualBackupRequest;
import com.syncduo.server.model.entity.BackupJobEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.restic.ls.Node;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.restic.ResticFacadeService;
import com.syncduo.server.util.EntityValidationUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/snapshots")
@Slf4j
@CrossOrigin
public class SnapshotsController {

    private final SyncFlowService syncFlowService;

    private final BackupJobService backupJobService;
    private final ResticFacadeService resticFacadeService;

    @Autowired
    public SnapshotsController(
            SyncFlowService syncFlowService,
            BackupJobService backupJobService,
            ResticFacadeService resticFacadeService) {
        this.syncFlowService = syncFlowService;
        this.backupJobService = backupJobService;
        this.resticFacadeService = resticFacadeService;
    }

    @PostMapping("/backup")
    public SnapshotsResponse<Void> backup(@RequestBody ManualBackupRequest manualBackupRequest) {
        try {
            EntityValidationUtil.isManualBackupRequestValid(manualBackupRequest);
            // 查询 syncflow
            SyncFlowEntity syncFlowEntity =
                    this.syncFlowService.getBySyncFlowId(manualBackupRequest.getInnerSyncFlowId());
            if (ObjectUtils.isEmpty(syncFlowEntity)) {
                return SnapshotsResponse.onError("backup failed. SyncFlow is deleted");
            }
            // backup
            this.resticFacadeService.manualBackup(syncFlowEntity);
            return SnapshotsResponse.onSuccess("backup success");
        } catch (SyncDuoException e) {
            return SnapshotsResponse.onError("backup failed. ex is %s".formatted(e.getMessage()));
        }
    }

    @GetMapping("/get-snapshot-files")
    public SnapshotsResponse<SnapshotFileInfo> getSnapshotFiles(
            @Param("snapshotId") String snapshotId,
            @Param("pathString") String pathString) {
        try {
            if (StringUtils.isAnyBlank(snapshotId, pathString)) {
                return SnapshotsResponse.onError("getSnapshotsFile failed. " +
                        "snapshotId or path is null.");
            }
            List<Node> nodeList = this.resticFacadeService.getSnapshotFileInfo(snapshotId, pathString);
            if (CollectionUtils.isEmpty(nodeList)) {
                return SnapshotsResponse.onSuccess("getSnapshotsFile success. there is no file.");
            }
            List<SnapshotFileInfo> result = new ArrayList<>();
            for (Node node : nodeList) {
                result.add(SnapshotFileInfo.getFromResticNode(node));
            }
            // restic ls 命令, 使用形如 /<folder1>/ 的方式查询, 还是会把 folder1 包含在查询结果中
            // 所以需要去除 pathString 的结果
            int deleteIndex = -1;
            for (int i = 0; i < result.size(); i++) {
                String path = result.get(i).getPath();
                if (pathString.equals(path) || pathString.equals(path + "/")) {
                    deleteIndex = i;
                    break;
                }
            }
            if (deleteIndex != -1) {
                result.remove(deleteIndex);
            }
            // 文件夹在最前面
            result.sort(Comparator.comparing(snapshotFileInfo -> {
                ResticNodeTypeEnum type = ResticNodeTypeEnum.fromString(snapshotFileInfo.getType());
                return type.equals(ResticNodeTypeEnum.DIRECTORY) ? 1 : -1;
            }));
            return SnapshotsResponse.onSuccess("getSnapshotsFile success.", result);
        } catch (Exception e) {
            return SnapshotsResponse.onError("getSnapshotsFile failed. ex is " + e.getMessage());
        }
    }

    @GetMapping("/get-snapshots")
    public SnapshotsResponse<SyncFlowWithSnapshots> getSnapshots(@Param("syncFlowId") String syncFlowId) {
        try {
            if (StringUtils.isBlank(syncFlowId)) {
                // 获取全部 snapshots
                List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
                if (CollectionUtils.isEmpty(allSyncFlow)) {
                    return SnapshotsResponse.onSuccess("没有可用的 syncflow");
                }
                List<SyncFlowWithSnapshots> result = new ArrayList<>();
                for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                    SyncFlowWithSnapshots tmp = this.getSyncFlowSnapshotsInfo(syncFlowEntity);
                    if (ObjectUtils.isEmpty(tmp)) {
                        continue;
                    }
                    result.add(tmp);
                }
                return SnapshotsResponse.onSuccess("成功", result);
            } else {
                // 参数检查
                long syncFlowIdLong = Long.parseLong(syncFlowId);
                // 获取对应的 snapshots
                SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(syncFlowIdLong);
                if (ObjectUtils.isEmpty(syncFlowEntity)) {
                    return SnapshotsResponse.onSuccess("syncflow not found");
                }
                SyncFlowWithSnapshots result = this.getSyncFlowSnapshotsInfo(syncFlowEntity);
                return SnapshotsResponse.onSuccess("成功", Collections.singletonList(result));
            }
        } catch (Exception e) {
            return SnapshotsResponse.onError("getSnapshots 失败. ex 是" + e.getMessage());
        }
    }

    private SyncFlowWithSnapshots getSyncFlowSnapshotsInfo(SyncFlowEntity syncFlowEntity) {
        List<BackupJobEntity> backupJobEntityList =
                this.backupJobService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        if (CollectionUtils.isEmpty(backupJobEntityList)) {
            return getSyncFlowSnapshotsInfo(syncFlowEntity, Collections.emptyList());
        }
        // 排序, 时间倒排
        backupJobEntityList.sort(Comparator.comparing(
                (backupJobEntity) -> (
                        backupJobEntity.getFinishedAt() != null) ?
                        backupJobEntity.getFinishedAt() :
                        backupJobEntity.getLastUpdatedTime(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        // snapshot 结果集
        List<SnapshotInfo> snapshotInfos = new ArrayList<>();
        for (BackupJobEntity backupJobEntity : backupJobEntityList) {
            // 添加到结果集
            snapshotInfos.add(SnapshotInfo.getFromBackupJobEntity(backupJobEntity));
        }
        // 处理 syncflow entity
        return getSyncFlowSnapshotsInfo(syncFlowEntity, snapshotInfos);
    }

    private static SyncFlowWithSnapshots getSyncFlowSnapshotsInfo(
            SyncFlowEntity syncFlowEntity,
            List<SnapshotInfo> snapshotInfos) {
        SyncFlowWithSnapshots syncFlowSnapshotsInfo = new SyncFlowWithSnapshots();
        syncFlowSnapshotsInfo.setSyncFlowId(syncFlowEntity.getSyncFlowId().toString());
        syncFlowSnapshotsInfo.setSyncFlowName(syncFlowEntity.getSyncFlowName());
        syncFlowSnapshotsInfo.setSourceFolderPath(syncFlowEntity.getSourceFolderPath());
        syncFlowSnapshotsInfo.setDestFolderPath(syncFlowEntity.getDestFolderPath());
        if (CollectionUtils.isNotEmpty(snapshotInfos)) {
            syncFlowSnapshotsInfo.setSnapshotInfoList(snapshotInfos);
        }
        return syncFlowSnapshotsInfo;
    }
}
