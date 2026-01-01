package com.syncduo.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.syncduo.server.core.engine.FlowEngine;
import com.syncduo.server.core.model.definition.FlowDefinition;
import com.syncduo.server.core.model.definition.FlowNode;
import com.syncduo.server.core.model.definition.ParamValue;
import com.syncduo.server.core.model.execution.FlowContext;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.mapper.SnapshotMetaMapper;
import com.syncduo.server.model.SystemSettings;
import com.syncduo.server.model.api.global.FlowResponse;
import com.syncduo.server.model.api.snapshot.RestoreRequest;
import com.syncduo.server.model.api.snapshot.SnapshotItemDTO;
import com.syncduo.server.model.db.SnapshotMetaEntity;
import com.syncduo.server.node.registry.FieldRegistry;
import com.syncduo.server.node.restic.model.SnapshotNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@Slf4j
@CrossOrigin(originPatterns = "*")
@RequestMapping("/restic")
@RequiredArgsConstructor
public class ResticController {
    private final FlowEngine flowEngine;
    private final SystemSettings systemSettings;
    private final SnapshotMetaMapper snapshotMetaMapper;

    @GetMapping("/get-all-snapshot")
    public FlowResponse<List<SnapshotMetaEntity>> getAllSnapshot() {
        LambdaQueryWrapper<SnapshotMetaEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SnapshotMetaEntity::getRecordDeleted, DeletedEnum.NOT_DELETED.getCode());
        queryWrapper.isNotNull(SnapshotMetaEntity::getSnapshotId);
        return FlowResponse.success(this.snapshotMetaMapper.selectList(queryWrapper));
    }

    @PostMapping("/get-snapshot-item")
    public FlowResponse<List<SnapshotItemDTO>> getSnapshotItem(
            @RequestBody SnapshotMetaEntity snapshotMetaEntity,
            @RequestParam(value = "filter", defaultValue = "/") String filter) {
        FlowDefinition tempFlow = buildLsFlow(snapshotMetaEntity, filter);
        FlowContext context;
        try {
            context = this.flowEngine.executeOnce(tempFlow).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new BusinessException("ls 执行失败", e);
        }
        List<SnapshotNode> snapshotNodes = FieldRegistry.getValue(FieldRegistry.RESTIC_SNAPSHOT_NODES, context);
        return FlowResponse.success(snapshotNodes.stream()
                .map(n -> new SnapshotItemDTO(
                        n.getName(),
                        n.getType(),
                        n.getPath(),
                        n.isDirectory() ? 0L : n.getSize().longValue(),
                        n.getCtime().toInstant()))
                .toList());
    }

    private FlowDefinition buildLsFlow(SnapshotMetaEntity snapshotMetaEntity, String filter) {
        return new FlowDefinition("restic-ls-temp", List.of(
                new FlowNode(
                        "1",
                        "fetch_snapshot_nodes",
                        Map.of(
                                FieldRegistry.RESTIC_SNAPSHOT_ID, new ParamValue(snapshotMetaEntity.getSnapshotId()),
                                FieldRegistry.RESTIC_LS_FILTER, new ParamValue(filter),
                                FieldRegistry.RESTIC_PASSWORD,
                                new ParamValue(systemSettings.getRestic().getBackupPassword()),
                                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                                new ParamValue(snapshotMetaEntity.getBackupRepository())
                        ),
                        List.of()
                )
        ));
    }

    @PostMapping("/download-restore-file")
    public ResponseEntity<Resource> download(
            @RequestBody RestoreRequest restoreRequest,
            @RequestParam("preview") Boolean isPreview) {
        // 将 item 转换为 nodes
        List<SnapshotNode> snapshotNodes = restoreRequest.getSnapshotItemDTOList().stream()
                .map(n -> new SnapshotNode()
                        .setName(n.getName())
                        .setPath(n.getPath())
                        .setType(n.getType()))
                .toList();
        FlowDefinition tempFlow = buildRestoreFlow(restoreRequest, snapshotNodes);
        FlowContext context;
        try {
            context = this.flowEngine.executeOnce(tempFlow).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new BusinessException("restore 执行失败", e.getCause());
        }
        Path file = FieldRegistry.getValue(FieldRegistry.RESTIC_RESTORE_RESULT, context);
        UrlResource urlResource;
        try {
            urlResource = new UrlResource(file.toUri());
        } catch (MalformedURLException e) {
            throw new BusinessException(("getDownloadFiles failed. " +
                    "restoreFile:%s can't convert to url.").formatted(file), e);
        }
        String fileName = file.getFileName().toString();
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

    private FlowDefinition buildRestoreFlow(RestoreRequest restoreRequest, List<SnapshotNode> snapshotNodes) {
        SnapshotMetaEntity snapshotMetaEntity = restoreRequest.getSnapshotMetaEntity();
        return new FlowDefinition("restic-ls-temp", List.of(
                new FlowNode(
                        "1",
                        "restore",
                        Map.of(
                                FieldRegistry.RESTIC_SNAPSHOT_ID, new ParamValue(snapshotMetaEntity.getSnapshotId()),
                                FieldRegistry.RESTIC_SNAPSHOT_NODES, new ParamValue(snapshotNodes),
                                FieldRegistry.RESTIC_PASSWORD,
                                new ParamValue(systemSettings.getRestic().getBackupPassword()),
                                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                                new ParamValue(snapshotMetaEntity.getBackupRepository())
                        ),
                        List.of()
                )
        ));
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
}
