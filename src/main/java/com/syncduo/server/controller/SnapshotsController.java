package com.syncduo.server.controller;

import com.syncduo.server.enums.ResticNodeTypeEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.ResourceNotFoundException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.api.snapshots.SnapshotInfo;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/snapshots")
@Slf4j
@CrossOrigin(originPatterns = "*")
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
    public SyncDuoHttpResponse<Void> backup(
            @RequestBody ManualBackupRequest manualBackupRequest)  {
        EntityValidationUtil.isManualBackupRequestValid(manualBackupRequest);
        // 查询 syncflow
        SyncFlowEntity syncFlowEntity =
                this.syncFlowService.getBySyncFlowId(manualBackupRequest.getInnerSyncFlowId());
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            throw new ResourceNotFoundException("backup failed. SyncFlow is deleted");
        }
        // backup
        this.resticFacadeService.backup(syncFlowEntity);
        return SyncDuoHttpResponse.success();
    }

    @GetMapping("/get-snapshot-files")
    public SyncDuoHttpResponse<List<SnapshotFileInfo>> getSnapshotFiles(
            @Param("backupJobId") String backupJobId,
            @Param("pathString") String pathString) {
        if (StringUtils.isAnyBlank(backupJobId, pathString)) {
            throw new ValidationException("getSnapshotsFile failed. backupJobId or path is null.");
        }
        // 查询 backup job entity
        BackupJobEntity backupJobEntity = this.backupJobService.getByBackupJobId(Long.parseLong(backupJobId));
        if (ObjectUtils.isEmpty(backupJobEntity)) {
            throw new ResourceNotFoundException("getSnapshotsFile failed. backupJobId not found.");
        }
        // 如果 backup 没有产生 snapshot, 则使用最新的 snapshot
        if (StringUtils.isBlank(backupJobEntity.getSnapshotId())) {
            backupJobEntity = this.backupJobService.getFirstValidSnapshotId(backupJobEntity);
            if (StringUtils.isBlank(backupJobEntity.getSnapshotId())) {
                return SyncDuoHttpResponse.success(null, "there is no snapshot");
            }
        }
        List<Node> nodeList = this.resticFacadeService.getSnapshotFileInfo(
                backupJobEntity.getSnapshotId(),
                pathString
        );
        if (CollectionUtils.isEmpty(nodeList)) {
            return SyncDuoHttpResponse.success(null, "getSnapshotsFile success. there is no file.");
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
        return SyncDuoHttpResponse.success(result);
    }

    @GetMapping("/get-all-syncflow-with-snapshots")
    public SyncDuoHttpResponse<List<SyncFlowWithSnapshots>> getAllSyncFlowWithSnapshots() {
        // 获取全部 syncflow
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return SyncDuoHttpResponse.success(Collections.emptyList(), "no snapshots");
        }
        // 每个 syncflow 获取所有 snapshots
        List<SyncFlowWithSnapshots> result = new ArrayList<>();
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            result.add(this.combineSyncFlowWithSnapshotInfo(syncFlowEntity));
        }
        return SyncDuoHttpResponse.success(result);
    }

    @GetMapping("/get-syncflow-with-snapshots")
    public SyncDuoHttpResponse<SyncFlowWithSnapshots> getSyncFlowWithSnapshots(
            @RequestParam("syncFlowId") String syncFlowId) {
        if (StringUtils.isBlank(syncFlowId)) {
            throw new ValidationException("getSyncFlowWithSnapshots failed. syncFlowId is null.");
        }
        SyncFlowEntity syncFlowEntity = this.syncFlowService.getBySyncFlowId(Long.parseLong(syncFlowId));
        if (ObjectUtils.isEmpty(syncFlowEntity)) {
            return SyncDuoHttpResponse.success(null, "syncFlow is deleted");
        }
        return SyncDuoHttpResponse.success(combineSyncFlowWithSnapshotInfo(syncFlowEntity));
    }

    @PostMapping("/submit-download-job")
    public SyncDuoHttpResponse<String> submitDownloadJob(@RequestBody List<SnapshotFileInfo> snapshotFileInfoList) {
        EntityValidationUtil.isSnapshotFileInfoListValid(snapshotFileInfoList);
        long restoreJobId = this.resticFacadeService.submitRestoreJob(snapshotFileInfoList);
        return SyncDuoHttpResponse.success(Long.toString(restoreJobId));
    }

    @GetMapping("/get-download-files")
    public ResponseEntity<Resource> getDownloadFiles(
            @RequestParam("restoreJobId") String restoreJobId,
            @RequestParam("isPreview") Boolean isPreview) {
        if (StringUtils.isBlank(restoreJobId)) {
            throw new ValidationException("getDownloadFiles failed. restoreJobId is blank");
        }
        long id;
        try {
            id = Long.parseLong(restoreJobId);
        } catch (Exception e) {
            throw new ValidationException("getDownloadFiles failed. restoreJobId can't convert to long");
        }
        Path restoreFile = this.resticFacadeService.getRestoreFile(id);
        if (ObjectUtils.isEmpty(restoreFile)) {
            return ResponseEntity.noContent().build();
        }
        UrlResource urlResource;
        try {
            urlResource = new UrlResource(restoreFile.toUri());
        } catch (MalformedURLException e) {
            throw new BusinessException(("getDownloadFiles failed. " +
                    "restoreFile:%s can't convert to url.").formatted(restoreFile), e);
        }
        String fileName = restoreFile.getFileName().toString();
        // 不是 preview 则直接返回文件
        if (!isPreview) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + fileName + "\"")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(urlResource);
        }
        // 是预览则判断 media type 并返回二进制流
        MediaType mediaType = determineContentType(fileName);
        if (ObjectUtils.isEmpty(mediaType)) {
            throw new BusinessException("previewFile failed. fileType not supported.");
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(urlResource);
    }

    private MediaType determineContentType(String filename) {
        // 根据文件扩展名返回对应的 MIME 类型
        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_JSON;
            case "txt" -> MediaType.parseMediaType("text/plain; charset=UTF-8"); // 明确指定字符集，避免中文乱码
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "png" -> MediaType.IMAGE_PNG;
            case "gif" -> MediaType.IMAGE_GIF;
            // ... 可以添加更多支持的类型
            default -> null;
        };
    }

    private SyncFlowWithSnapshots combineSyncFlowWithSnapshotInfo(SyncFlowEntity syncFlowEntity) {
        // syncflow entity 转换为 SyncFlowWith Snapshots
        SyncFlowWithSnapshots syncFlowWithSnapshots = new SyncFlowWithSnapshots();
        syncFlowWithSnapshots.setSyncFlowId(syncFlowEntity.getSyncFlowId().toString());
        syncFlowWithSnapshots.setSyncFlowName(syncFlowEntity.getSyncFlowName());
        syncFlowWithSnapshots.setSourceFolderPath(syncFlowEntity.getSourceFolderPath());
        syncFlowWithSnapshots.setDestFolderPath(syncFlowEntity.getDestFolderPath());
        // 获取 syncflow 对应的所有 backup job
        List<BackupJobEntity> backupJobEntityList =
                this.backupJobService.getBySyncFlowId(syncFlowEntity.getSyncFlowId());
        if (CollectionUtils.isEmpty(backupJobEntityList)) {
            return syncFlowWithSnapshots;
        }
        // 排序, 时间倒排
        backupJobEntityList.sort(Comparator.comparing(
                (backupJobEntity) -> (
                        backupJobEntity.getFinishedAt() != null) ?
                        backupJobEntity.getFinishedAt() :
                        backupJobEntity.getLastUpdatedTime(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        // backup job 转 snapshotInfo
        List<SnapshotInfo> snapshotInfos = new ArrayList<>();
        for (BackupJobEntity backupJobEntity : backupJobEntityList) {
            // 添加到结果集
            snapshotInfos.add(SnapshotInfo.getFromBackupJobEntity(backupJobEntity));
        }
        // syncflow entity 和 snapshotInfos 整合
        syncFlowWithSnapshots.setSnapshotInfoList(snapshotInfos);
        return syncFlowWithSnapshots;
    }
}
