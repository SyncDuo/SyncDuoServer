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
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
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
            @Param("backupJobId") String backupJobId,
            @Param("pathString") String pathString) {
        try {
            if (StringUtils.isAnyBlank(backupJobId, pathString)) {
                return SnapshotsResponse.onError("getSnapshotsFile failed. " +
                        "backupJobId or path is null.");
            }
            // 判断 backup id 是否有 snapshot
            BackupJobEntity backupJobEntity = this.backupJobService.getByBackupJobId(Long.parseLong(backupJobId));
            if (ObjectUtils.isEmpty(backupJobEntity)) {
                return SnapshotsResponse.onError("getSnapshotsFile failed. backupJobId not found.");
            }
            // 如果 backup 没有产生 snapshot, 则使用最新的 snapshot
            if (StringUtils.isBlank(backupJobEntity.getSnapshotId())) {
                backupJobEntity = this.backupJobService.getFirstValidSnapshotId(backupJobEntity);
                if (ObjectUtils.isEmpty(backupJobEntity) ||
                        StringUtils.isBlank(backupJobEntity.getSnapshotId())) {
                    return SnapshotsResponse.onSuccess("getSnapshotsFile success. there is no snapshot");
                }
            }
            List<Node> nodeList = this.resticFacadeService.getSnapshotFileInfo(
                    backupJobEntity.getSnapshotId(),
                    pathString
            );
            if (CollectionUtils.isEmpty(nodeList)) {
                return SnapshotsResponse.onSuccess("getSnapshotsFile success. there is no file.");
            }
            List<SnapshotFileInfo> result = new ArrayList<>();
            for (Node node : nodeList) {
                result.add(SnapshotFileInfo.getFromResticNode(backupJobEntity.getSnapshotId(), node));
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
                return type.equals(ResticNodeTypeEnum.DIRECTORY) ? -1 : 1;
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

    @PostMapping("/download-snapshot-files")
    public ResponseEntity<Resource> downloadSnapshotFiles(
            @RequestBody List<SnapshotFileInfo> snapshotFileInfoList) throws SyncDuoException {
        try {
            EntityValidationUtil.isSnapshotFileInfoListValid(snapshotFileInfoList);
        } catch (SyncDuoException e) {
            throw new SyncDuoException(HttpStatus.BAD_REQUEST, "downloadSnapshotFiles failed. ", e);
        }
        try {
            Path zipFile = this.resticFacadeService.restoreFiles(snapshotFileInfoList);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + zipFile.getFileName() + "\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new UrlResource(zipFile.toUri()));
        } catch (Exception e) {
            throw new SyncDuoException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "downloadSnapshotFiles failed.", e);
        }
    }

    @PostMapping("/download-snapshot-file")
    public ResponseEntity<Resource> downloadSnapshotFile(
            @RequestBody SnapshotFileInfo snapshotFileInfo) throws SyncDuoException {
        try {
            EntityValidationUtil.isSnapshotFileInfoListValid(Collections.singletonList(snapshotFileInfo));
            if (!ResticNodeTypeEnum.FILE.getType().equals(snapshotFileInfo.getType())) {
                throw new SyncDuoException("downloadSnapshotFile failed. snapshotFileInfo is not a file.");
            }
        } catch (SyncDuoException e) {
            throw new SyncDuoException(HttpStatus.BAD_REQUEST, "downloadSnapshotFile failed. ", e);
        }
        try {
            Path restoreFile = this.resticFacadeService.restoreFile(snapshotFileInfo);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + snapshotFileInfo.getFileName() + "\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new UrlResource(restoreFile.toUri()));
        } catch (Exception e) {
            throw new SyncDuoException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "downloadSnapshotFile failed. ",
                    e);
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
