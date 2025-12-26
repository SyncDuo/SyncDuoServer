package com.syncduo.server;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.controller.FileSystemAccessController;
import com.syncduo.server.controller.SnapshotsController;
import com.syncduo.server.controller.SyncFlowController;
import com.syncduo.server.controller.SystemInfoController;
import com.syncduo.server.enums.*;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.api.filesystem.Folder;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import com.syncduo.server.model.api.snapshots.SnapshotFileInfo;
import com.syncduo.server.model.api.snapshots.SnapshotInfo;
import com.syncduo.server.model.api.snapshots.SyncFlowWithSnapshots;
import com.syncduo.server.model.api.syncflow.*;
import com.syncduo.server.model.api.systeminfo.SystemInfo;
import com.syncduo.server.model.api.systeminfo.SystemSettings;
import com.syncduo.server.model.entity.BackupJobEntity;
import com.syncduo.server.model.entity.RestoreJobEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.CopyJobService;
import com.syncduo.server.service.db.impl.RestoreJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.service.restic.ResticFacadeService;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.workflow.controller.FlowEditorController;
import com.syncduo.server.workflow.controller.FlowInfoController;
import com.syncduo.server.workflow.core.engine.FlowEngine;
import com.syncduo.server.workflow.core.enums.ParamSourceType;
import com.syncduo.server.workflow.core.model.definition.FlowDefinition;
import com.syncduo.server.workflow.core.model.definition.FlowNode;
import com.syncduo.server.workflow.core.model.definition.ParamValue;
import com.syncduo.server.workflow.mapper.FlowDefinitionMapper;
import com.syncduo.server.workflow.mapper.FlowExecutionMapper;
import com.syncduo.server.workflow.mapper.NodeExecutionMapper;
import com.syncduo.server.workflow.mapper.SnapshotMetaMapper;
import com.syncduo.server.workflow.model.api.editor.CreateFlowRequest;
import com.syncduo.server.workflow.model.api.editor.FieldSchemaDTO;
import com.syncduo.server.workflow.model.api.global.FlowResponse;
import com.syncduo.server.workflow.model.api.info.FlowInfoDTO;
import com.syncduo.server.workflow.model.db.FlowDefinitionEntity;
import com.syncduo.server.workflow.model.db.FlowExecutionEntity;
import com.syncduo.server.workflow.model.db.SnapshotMetaEntity;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.profiles.active=test")
@Slf4j
class SyncDuoServerApplicationTests {

    private final SyncFlowController syncFlowController;

    private final FlowEditorController flowEditorController;

    private final FlowInfoController flowInfoController;

    private final SyncFlowService syncFlowService;

    private final FolderWatcher folderWatcher;

    private final RcloneFacadeService rcloneFacadeService;

    private final ResticFacadeService resticFacadeService;

    private final CopyJobService copyJobService;

    private final BackupJobService backupJobService;

    private final RestoreJobService restoreJobService;

    private final SnapshotsController snapshotsController;

    private final SystemInfoController systemInfoController;

    private final FileSystemAccessController fileSystemAccessController;

    private final FlowExecutionMapper flowExecutionMapper;

    private final NodeExecutionMapper nodeExecutionMapper;

    private final FlowDefinitionMapper flowDefinitionMapper;

    private final SnapshotMetaMapper snapshotMetaMapper;

    private SyncFlowEntity syncFlowEntity;

    private List<FlowNode> nodeList;

    private List<FieldSchemaDTO> fieldSchemaDTOList;

    private final FlowEngine flowEngine;

    @Value("${syncduo.server.test.sourceFolder}")
    private String sourceFolderPath;

    @Value("${syncduo.server.test.contentParentFolder}")
    private String contentFolderParentPath;

    @Value("${syncduo.server.restic.backupPath}")
    private String backupPath;

    @Value("${syncduo.server.restic.restorePath}")
    private String restorePath;

    @Value("${syncduo.server.restic.restoreAgeSec}")
    private long restoreAgeSec;

    // delay 函数延迟的时间, 单位"秒"
    private static final int DELAY_UNIT = 18;

    @Autowired
    public SyncDuoServerApplicationTests(
            SyncFlowController syncFlowController,
            FlowEditorController flowEditorController,
            FlowInfoController flowInfoController,
            SyncFlowService syncFlowService,
            FolderWatcher folderWatcher,
            RcloneFacadeService rcloneFacadeService,
            ResticFacadeService resticFacadeService,
            CopyJobService copyJobService,
            BackupJobService backupJobService,
            RestoreJobService restoreJobService,
            SnapshotsController snapshotsController,
            SystemInfoController systemInfoController,
            FileSystemAccessController fileSystemAccessController,
            FlowExecutionMapper flowExecutionMapper,
            NodeExecutionMapper nodeExecutionMapper,
            FlowDefinitionMapper flowDefinitionMapper,
            SnapshotMetaMapper snapshotMetaMapper,
            FlowEngine flowEngine) {
        this.syncFlowController = syncFlowController;
        this.flowEditorController = flowEditorController;
        this.flowInfoController = flowInfoController;
        this.syncFlowService = syncFlowService;
        this.folderWatcher = folderWatcher;
        this.rcloneFacadeService = rcloneFacadeService;
        this.resticFacadeService = resticFacadeService;
        this.copyJobService = copyJobService;
        this.backupJobService = backupJobService;
        this.restoreJobService = restoreJobService;
        this.snapshotsController = snapshotsController;
        this.systemInfoController = systemInfoController;
        this.fileSystemAccessController = fileSystemAccessController;
        this.flowExecutionMapper = flowExecutionMapper;
        this.nodeExecutionMapper = nodeExecutionMapper;
        this.flowDefinitionMapper = flowDefinitionMapper;
        this.snapshotMetaMapper = snapshotMetaMapper;
        this.flowEngine = flowEngine;
    }

    @Test
    void RcloneCopyNodeTest() {
        FlowNode node1 = new FlowNode(
                "1",
                "oneway_sync",
                Map.of(
                        FieldRegistry.SOURCE_DIRECTORY, new ParamValue(this.sourceFolderPath, ParamSourceType.MANUAL),
                        FieldRegistry.DST_DIRECTORY, new ParamValue(this.contentFolderParentPath, ParamSourceType.MANUAL)
                ),
                List.of()
        );
        this.flowEngine.execute(1L, new FlowDefinition("tmp", List.of(node1)));
        waitSec(1000);
        List<FlowExecutionEntity> dbResult = this.flowExecutionMapper.selectList(new QueryWrapper<>());
        log.debug(dbResult.toString());
    }

    @Test
    void PersistSnapDataNodeTest() {
        FlowNode node1 = new FlowNode(
                "1",
                "backup",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL),
                        FieldRegistry.SOURCE_DIRECTORY, new ParamValue(this.sourceFolderPath, ParamSourceType.MANUAL)
                ),
                List.of("2")
        );
        FlowNode node2 = new FlowNode(
                "2",
                "persist_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                Collections.emptyList()
        );
        FlowNode node3 = new FlowNode(
                "3",
                "persist_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                Collections.emptyList()
        );
        FlowDefinition tmp = new FlowDefinition("tmp", List.of(node1, node2, node3));
        this.flowEngine.execute(1L, tmp);
        waitSec(15);
        List<SnapshotMetaEntity> allResult = this.snapshotMetaMapper.selectList(new QueryWrapper<>());
        assertEquals(1, allResult.size());
    }

    @Test
    void CreateDataInDB() {
        for (int i = 0; i < 4; i++) {
            FlowResponse<FlowDefinitionEntity> response = this.flowEditorController.createFlow(new CreateFlowRequest(
                    "tmp" + i,
                    "0 0 18 * * MON-FRI",
                    "tmp" + i,
                    this.nodeList,
                    this.fieldSchemaDTOList
            ));
            FlowDefinition definition = response.getData().getDefinition();
            Future<?> task = this.flowEngine.execute(response.getData().getFlowDefinitionId(), definition);
            while (!task.isDone()) {
                waitSec(5);
            }
        }
    }

    @Test
    void ShouldReturnTrueWhenGetFlowInfo() {
        FlowResponse<FlowDefinitionEntity> response = this.flowEditorController.createFlow(new CreateFlowRequest(
                "tmp",
                "0 0 18 * * MON-FRI",
                "tmp",
                this.nodeList,
                this.fieldSchemaDTOList
        ));
        assertEquals(200, response.getStatusCode());
        FlowResponse<List<FlowInfoDTO>> response1 = this.flowInfoController.getAllFlowInfo();
        assertEquals(200, response1.getStatusCode());
        List<FlowInfoDTO> flowInfoDTOList = response1.getData();
        assertEquals(1, flowInfoDTOList.size());
        FlowInfoDTO flowInfoDTO = flowInfoDTOList.get(0);
        FlowDefinitionEntity entity = response.getData();
        assertEquals(flowInfoDTO.getFlowDefinitionId(), entity.getFlowDefinitionId());
        assertEquals(flowInfoDTO.getFlowName(), entity.getName());
        assertEquals(flowInfoDTO.getCronConfig(), entity.getCronConfig());
    }

    @Test
    void ShouldReturnTrueWhenCronInvalid() {
        assertThrows(ValidationException.class, () -> this.flowEditorController.createFlow(new CreateFlowRequest(
                "tmp",
                "0 0 18 * *",
                "tmp",
                this.nodeList,
                this.fieldSchemaDTOList
        )));
    }

    @Test
    void ShouldReturnTrueWhenFlowNameDuplicate() {
        FlowResponse<FlowDefinitionEntity> response = this.flowEditorController.createFlow(new CreateFlowRequest(
                "tmp",
                "0 0 18 * * MON-FRI",
                "tmp",
                this.nodeList,
                this.fieldSchemaDTOList
        ));
        assertEquals(200, response.getStatusCode());
        assertThrows(BusinessException.class, () -> this.flowEditorController.createFlow(new CreateFlowRequest(
                "tmp",
                "0 0 18 * * MON-FRI",
                "tmp",
                this.nodeList,
                this.fieldSchemaDTOList
        )));
    }


    @Test
    void ShouldReturnTrueWhenNodeDuplicateId() {
        FlowResponse<Void> response = this.flowEditorController.validNodes(this.nodeList);
        assertEquals(200, response.getStatusCode());
        this.nodeList.get(0).setNodeId(this.nodeList.get(1).getNodeId());
        assertThrows(ValidationException.class, () -> this.flowEditorController.validNodes(nodeList));
    }

    @Test
    void ShouldReturnTrueWhenNodeCyclic() {
        FlowResponse<Void> response = this.flowEditorController.validNodes(this.nodeList);
        assertEquals(200, response.getStatusCode());
        this.nodeList.get(1).setNextNodeIds(List.of("1"));
        assertThrows(ValidationException.class, () -> this.flowEditorController.validNodes(nodeList));
    }

    @Test
    void ShouldReturnTrueWhenNodeNotImpl() {
        FlowResponse<Void> response = this.flowEditorController.validNodes(this.nodeList);
        assertEquals(200, response.getStatusCode());
        this.nodeList.get(0).setName("aaa");
        assertThrows(ValidationException.class, () -> this.flowEditorController.validNodes(nodeList));
    }

    @Test
    void ShouldReturnTrueWhenGetParam() {
        FlowResponse<List<FieldSchemaDTO>> response = this.flowEditorController.getFieldSchema(this.nodeList);
        assertEquals(200, response.getStatusCode());
        List<FieldSchemaDTO> fieldSchemaDTOList = response.getData();
        assertEquals(fieldSchemaDTOList.size(), this.fieldSchemaDTOList.size());
        Map<String, FieldSchemaDTO> map = fieldSchemaDTOList.stream()
                .collect(Collectors.toMap(FieldSchemaDTO::getNodeId, n -> n));
        for (FieldSchemaDTO fieldSchemaDTO : this.fieldSchemaDTOList) {
            String nodeId = fieldSchemaDTO.getNodeId();
            compareFieldSchemaDTO(fieldSchemaDTO, map.get(nodeId));
            map.remove(nodeId);
        }
        assertEquals(0, map.size());
    }

    @Test
    void ShouldReturnTrueWhenValidParamSimpleType() {
        this.fieldSchemaDTOList.get(0).addFieldSchema(
                FieldRegistry.SOURCE_DIRECTORY,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.SOURCE_DIRECTORY),
                123
        );
        assertThrows(ValidationException.class, () -> this.flowEditorController.validFieldSchema(this.fieldSchemaDTOList));
    }

    @Test
    void ShouldReturnTrueWhenValidParamPojo() {
        this.fieldSchemaDTOList.get(0).addFieldSchema(
                FieldRegistry.RESTIC_BACKUP_RESULT,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.RESTIC_BACKUP_RESULT),
                ResticExecResult.success(ResticExitCodeEnum.SUCCESS, "456")
        );
        assertThrows(ValidationException.class, () -> this.flowEditorController.validFieldSchema(this.fieldSchemaDTOList));
        this.fieldSchemaDTOList.get(0).addFieldSchema(
                FieldRegistry.RESTIC_BACKUP_RESULT,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.RESTIC_BACKUP_RESULT),
                ResticExecResult.failed(ResticExitCodeEnum.SUCCESS, "789")
        );
        assertThrows(ValidationException.class, () -> this.flowEditorController.validFieldSchema(this.fieldSchemaDTOList));
    }

    @Test
    void ShouldReturnTrueWhenValidParamCollections() {
        this.fieldSchemaDTOList.get(0).addFieldSchema(
                FieldRegistry.DEDUPLICATE_FILES,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.DEDUPLICATE_FILES),
                List.of(123, 456)
        );
        assertThrows(ValidationException.class, () -> this.flowEditorController.validFieldSchema(this.fieldSchemaDTOList));

    }

    private static void compareFieldSchemaDTO(
            FieldSchemaDTO src,
            FieldSchemaDTO target) {
        assertEquals(src.getNodeId(), target.getNodeId());
        assertEquals(src.getNodeName(), target.getNodeName());
        assertEquals(src.getFieldSchemaMap().size(), target.getFieldSchemaMap().size());
        src.getFieldSchemaMap().forEach((k, v) -> {
            FieldSchemaDTO.FieldSchema fieldSchema = target.getFieldSchema(k);
            assertNotNull(fieldSchema);
            assertEquals(v.sourceType(), fieldSchema.sourceType());
            assertEquals(v.typeReference(), fieldSchema.typeReference());
        });
    }

    @Test
    void ShouldReturnTrueWhenGettingHostName() {
        SyncDuoHttpResponse<String> hostName = this.fileSystemAccessController.getHostName();
        assert StringUtils.isNotBlank(hostName.getData());
    }

    @Test
    void ShouldReturnTrueWhenGettingSubfolders() {
        SyncDuoHttpResponse<List<Folder>> subfolders =
                this.fileSystemAccessController.getSubfolders(sourceFolderPath);
        List<Folder> data = subfolders.getData();
        assert CollectionUtils.isNotEmpty(data);
        for (Folder folder : data) {
            assert ObjectUtils.isNotEmpty(folder);
        }
    }

    @Test
    void ShouldReturnTrueWhenGettingSystemSettings() {
        SyncDuoHttpResponse<SystemSettings> result = this.systemInfoController.getSystemSettings();
        assert result.getStatusCode() == 200;
        SystemSettings systemSettings = result.getData();
        assert ObjectUtils.isNotEmpty(systemSettings);
        assert ObjectUtils.allNotNull(
                systemSettings.getSystem(),
                systemSettings.getRclone(),
                systemSettings.getRestic());
    }

    @Test
    void ShouldReturnTrueWhenGettingSystemInfo() {
        // 创建 syncflow
        createSyncFlow(null);
        SyncDuoHttpResponse<SystemInfo> result = this.systemInfoController.getSystemInfo();
        assert result.getStatusCode() == 200;
        SystemInfo systemInfo = result.getData();
        assert ObjectUtils.isNotEmpty(systemInfo);
        assert ObjectUtils.allNotNull(
                systemInfo.getFolderStats(),
                systemInfo.getWatchers(),
                systemInfo.getSyncFlowNumber()
        );
        assert !StringUtils.isAnyBlank(
                systemInfo.getFileCopyRate(),
                systemInfo.getUptime()
        );
    }

    @Test
    void ShouldReturnTrueWhenDownloadFileCache() throws SyncDuoException, IOException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.backup();
        // 获取 snapshot info
        SyncDuoHttpResponse<SyncFlowWithSnapshots> syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        assert CollectionUtils.isNotEmpty(syncFlowWithSnapshots.getData().getSnapshotInfoList());
        // 获取 snapshot file info
        SyncDuoHttpResponse<List<SnapshotFileInfo>> snapshotFileInfoResponse = this.snapshotsController.getSnapshotFiles(
                syncFlowWithSnapshots.getData().getSnapshotInfoList().get(0).getBackupJobId(),
                "/"
        );
        // 下载文件
        for (SnapshotFileInfo snapshotFileInfo : snapshotFileInfoResponse.getData()) {
            if (snapshotFileInfo.getType().equals(ResticNodeTypeEnum.FILE.getType())) {
                ResponseEntity<Resource> response =
                        this.downloadSnapshotFiles(Collections.singletonList(snapshotFileInfo));
                assert response.getStatusCode() == HttpStatus.OK;
                assert ObjectUtils.isNotEmpty(response.getBody());
                assert response.getBody().contentLength() > 0;
                String headersString = response.getHeaders().toString();
                assert headersString.contains(HttpHeaders.CONTENT_DISPOSITION) &&
                        headersString.contains(snapshotFileInfo.getFileName());
                assert headersString.contains(MediaType.APPLICATION_OCTET_STREAM.toString());
            }
        }
        // 验证 restore 数据库记录是否生成
        List<RestoreJobEntity> restoreJobEntities = this.restoreJobService.list();
        assert CollectionUtils.isNotEmpty(restoreJobEntities);
        for (RestoreJobEntity restoreJobEntity : restoreJobEntities) {
            assert restoreJobEntity.getRestoreJobStatus().equals(CommonStatus.SUCCESS.name());
        }
        // 再次下载文件
        for (SnapshotFileInfo snapshotFileInfo : snapshotFileInfoResponse.getData()) {
            if (snapshotFileInfo.getType().equals(ResticNodeTypeEnum.FILE.getType())) {
                ResponseEntity<Resource> response =
                        this.downloadSnapshotFiles(Collections.singletonList(snapshotFileInfo));
                assert response.getStatusCode() == HttpStatus.OK;
                assert ObjectUtils.isNotEmpty(response.getBody());
                assert response.getBody().contentLength() > 0;
                String headersString = response.getHeaders().toString();
                assert headersString.contains(HttpHeaders.CONTENT_DISPOSITION) &&
                        headersString.contains(snapshotFileInfo.getFileName());
                assert headersString.contains(MediaType.APPLICATION_OCTET_STREAM.toString());
            }
        }
        // 验证走了缓存, 因为 restore 数据库记录没有生成
        assert restoreJobEntities.size() == this.restoreJobService.list().size();
    }

    @Test
    void ShouldReturnTrueWhenDownloadFile() throws SyncDuoException, IOException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.backup();
        // 获取 snapshot info
        SyncDuoHttpResponse<SyncFlowWithSnapshots> syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        assert CollectionUtils.isNotEmpty(syncFlowWithSnapshots.getData().getSnapshotInfoList());
        // 获取 snapshot file info, 使用 TestFolder1_1 测试嵌套的文件夹
        SyncDuoHttpResponse<List<SnapshotFileInfo>> snapshotFileInfoResponse = this.snapshotsController.getSnapshotFiles(
                syncFlowWithSnapshots.getData().getSnapshotInfoList().get(0).getBackupJobId(),
                "/TestFolder1_1"
        );
        // 下载文件
        for (SnapshotFileInfo snapshotFileInfo : snapshotFileInfoResponse.getData()) {
            if (snapshotFileInfo.getType().equals(ResticNodeTypeEnum.FILE.getType())) {
                ResponseEntity<Resource> response =
                        this.downloadSnapshotFiles(Collections.singletonList(snapshotFileInfo));
                assert response.getStatusCode() == HttpStatus.OK;
                assert ObjectUtils.isNotEmpty(response.getBody());
                assert response.getBody().contentLength() > 0;
                String headersString = response.getHeaders().toString();
                assert headersString.contains(HttpHeaders.CONTENT_DISPOSITION) &&
                        headersString.contains(snapshotFileInfo.getFileName());
                assert headersString.contains(MediaType.APPLICATION_OCTET_STREAM.toString());
            }
        }
        // 验证 restore 数据库记录是否生成
        List<RestoreJobEntity> restoreJobEntities = this.restoreJobService.list();
        assert CollectionUtils.isNotEmpty(restoreJobEntities);
        for (RestoreJobEntity restoreJobEntity : restoreJobEntities) {
            assert restoreJobEntity.getRestoreJobStatus().equals(CommonStatus.SUCCESS.name());
        }
        // 验证临时文件是否删除
        waitSec(restoreAgeSec * 2);
        assert CollectionUtils.isEmpty(FilesystemUtil.getSubFolders(restorePath));
    }

    @Test
    void ShouldReturnTrueWhenDownloadFiles() throws SyncDuoException, IOException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.backup();
        // 获取 snapshot info
        SyncDuoHttpResponse<SyncFlowWithSnapshots> syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        assert CollectionUtils.isNotEmpty(syncFlowWithSnapshots.getData().getSnapshotInfoList());
        // 获取 snapshot file info
        SyncDuoHttpResponse<List<SnapshotFileInfo>> snapshotFileInfoResponse = this.snapshotsController.getSnapshotFiles(
                syncFlowWithSnapshots.getData().getSnapshotInfoList().get(0).getBackupJobId(),
                "/"
        );
        // 下载多个文件
        ResponseEntity<Resource> response = this.downloadSnapshotFiles(snapshotFileInfoResponse.getData());
        assert response.getStatusCode() == HttpStatus.OK;
        assert ObjectUtils.isNotEmpty(response.getBody());
        assert response.getBody().contentLength() > 0;
        String headersString = response.getHeaders().toString();
        assert headersString.contains(HttpHeaders.CONTENT_DISPOSITION) && headersString.contains("zip");
        assert headersString.contains(MediaType.APPLICATION_OCTET_STREAM.toString());
        // 验证 restore 数据库记录是否生成
        List<RestoreJobEntity> restoreJobEntities = this.restoreJobService.list();
        assert CollectionUtils.isNotEmpty(restoreJobEntities);
        for (RestoreJobEntity restoreJobEntity : restoreJobEntities) {
            assert restoreJobEntity.getRestoreJobStatus().equals(CommonStatus.SUCCESS.name());
        }
        // 验证临时文件是否删除
        waitSec(restoreAgeSec * 2);
        assert CollectionUtils.isEmpty(FilesystemUtil.getSubFolders(restorePath));
    }

    @Test
    void ShouldReturnTrueWhenGetAllSyncFlowWithSnapshots() throws SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 获取 全部 syncflow with snapshots
        SyncDuoHttpResponse<List<SyncFlowWithSnapshots>> allSyncFlowWithSnapshots =
                this.snapshotsController.getAllSyncFlowWithSnapshots();
        assert CollectionUtils.isNotEmpty(allSyncFlowWithSnapshots.getData());
        assert CollectionUtils.isEmpty(allSyncFlowWithSnapshots.getData().get(0).getSnapshotInfoList());
        // 手动触发 backup job
        this.backup();
        allSyncFlowWithSnapshots = this.snapshotsController.getAllSyncFlowWithSnapshots();
        assert CollectionUtils.isNotEmpty(allSyncFlowWithSnapshots.getData());
    }

    @Test
    void ShouldReturnTureWhenBackupTwice() throws SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.backup();
        // 获取 snapshot info
        SyncDuoHttpResponse<SyncFlowWithSnapshots> syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        assert CollectionUtils.isNotEmpty(syncFlowWithSnapshots.getData().getSnapshotInfoList());
        SnapshotInfo snapshotInfo = syncFlowWithSnapshots.getData().getSnapshotInfoList().get(0);
        assert snapshotInfo.getBackupJobStatus().equals(CommonStatus.SUCCESS.name());
        // 再手动触发 backup
        this.backup();
        syncFlowWithSnapshots =
                this.snapshotsController.getSyncFlowWithSnapshots(syncFlowEntity.getSyncFlowId().toString());
        assert ObjectUtils.isNotEmpty(syncFlowWithSnapshots.getData());
        List<SnapshotInfo> snapshotInfoList = syncFlowWithSnapshots.getData().getSnapshotInfoList();
        assert StringUtils.isBlank(snapshotInfoList.get(0).getSnapshotId());
    }

    @Test
    void ShouldReturnTrueWhenBackup() throws SyncDuoException {
        // 创建 syncflow
        createSyncFlow(null);
        // 手动触发 backup job
        this.backup();
        // 获取 copy job
        List<BackupJobEntity> result = this.backupJobService.getBySyncFlowId(this.syncFlowEntity.getSyncFlowId());
        assert CollectionUtils.isNotEmpty(result);
        for (BackupJobEntity backupJobEntity : result) {
            log.debug("backJobEntity is {}", backupJobEntity.toString());
            assert backupJobEntity.getBackupJobStatus().equals(CommonStatus.SUCCESS.name());
        }
    }

    @Test
    void ShouldReturnTrueWhenResumeSyncFlow() throws SyncDuoException, IOException, ExecutionException, InterruptedException {
        // 创建 syncflow
        createSyncFlow(null);
        // 停止 syncflow
        this.syncFlowController.changeSyncFlowStatus(
                ChangeSyncFlowStatusRequest.builder()
                        .syncFlowId(this.syncFlowEntity.getSyncFlowId().toString())
                        .syncFlowStatus(SyncFlowStatusEnum.PAUSE.name())
                        .build()
        );
        // 源文件夹创建文件
        FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // source and dest should be desync
        assert !this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity).get();
        // 恢复 syncflow
        this.syncFlowController.changeSyncFlowStatus(
                ChangeSyncFlowStatusRequest.builder()
                        .syncFlowId(this.syncFlowEntity.getSyncFlowId().toString())
                        .syncFlowStatus(SyncFlowStatusEnum.RESUME.name())
                        .build()
        );
        waitAllFileHandle();
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity).get();
    }

    @Test
    void ShouldReturnTrueWhenDeleteSyncFlow()
            throws IOException, SyncDuoException, ExecutionException, InterruptedException {
        // 创建 syncflow
        createSyncFlow("[\"txt\"]");
        waitAllFileHandle();
        // 删除 syncflow
        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSyncFlowId(syncFlowEntity.getSyncFlowId().toString());
        this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        waitSec(5 * 2);
        assert ObjectUtils.isEmpty(this.syncFlowService.getBySyncFlowId(syncFlowEntity.getSyncFlowId()));
        // 源文件夹创建文件
        FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // source and dest is desync
        assert !this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity).get();
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByDeleteFile()
            throws IOException, SyncDuoException, ExecutionException, InterruptedException {
        // 创建 syncflow
        createSyncFlow(null);
        waitAllFileHandle();
        // 源文件夹删除文件
        FileOperationTestUtil.deleteFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // 判断 fileEvent 是否处理正确, 即 onewaycheck 的结果是 true
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity).get();
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByModifyFile()
            throws IOException, SyncDuoException, ExecutionException, InterruptedException {
        // 创建 syncflow
        createSyncFlow(null);
        // 源文件夹修改文件
        FileOperationTestUtil.modifyFile(Path.of(sourceFolderPath), 2);
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // 判断 fileEvent 是否处理正确, 即 onewaycheck 的结果是 true
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity).get();
    }

    @Test
    void ShouldReturnTrueWhenTriggerWatcherByCreateFile()
            throws IOException, SyncDuoException, ExecutionException, InterruptedException {
        // 创建 syncflow
        createSyncFlow(null);
        // 源文件夹创建文件
        FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        // 判断 fileEvent 是否处理正确, 即 onewaycheck 的结果是 true
        assert this.rcloneFacadeService.oneWayCheck(this.syncFlowEntity).get();
    }

    @Test
    void ShouldReturnTrueWhenCreateSyncFlowWithFilter() throws SyncDuoException, IOException {
        String filterCriteria = "[\"*.bin\"]";
        createSyncFlow(filterCriteria);
        List<Path> allFile = FilesystemUtil.getAllFile(Path.of(this.syncFlowEntity.getDestFolderPath()));
        for (Path path : allFile) {
            assert !path.getFileName().toString().contains("bin");
        }
        // 源文件创建文件
        FileOperationTestUtil.createTxtAndBinFile(Path.of(sourceFolderPath));
        // 等待文件处理
        waitSingleFileHandle(sourceFolderPath);
        allFile = FilesystemUtil.getAllFile(Path.of(this.syncFlowEntity.getDestFolderPath()));
        for (Path path : allFile) {
            assert !path.getFileName().toString().contains("bin");
        }
    }

    @Test
    void ShouldReturnTrueWhenCreateAndDeleteSyncFlow() {
        // reactive syncflow
        createSyncFlow(null);
        // 删除 syncflow
        DeleteSyncFlowRequest deleteSyncFlowRequest = new DeleteSyncFlowRequest();
        deleteSyncFlowRequest.setSyncFlowId(this.syncFlowEntity.getSyncFlowId().toString());
        SyncDuoHttpResponse<Void> syncFlowResponse1 = this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        assert syncFlowResponse1.getStatusCode() == 200;
        // backup only syncflow
        createBackupOnlySyncFlow();
        // 删除 syncflow
        deleteSyncFlowRequest.setSyncFlowId(this.syncFlowEntity.getSyncFlowId().toString());
        syncFlowResponse1 = this.syncFlowController.deleteSyncFlow(deleteSyncFlowRequest);
        assert syncFlowResponse1.getStatusCode() == 200;
    }

    ResponseEntity<Resource> downloadSnapshotFiles(List<SnapshotFileInfo> snapshotFileInfoList) {
        SyncDuoHttpResponse<String> response = this.snapshotsController.submitDownloadJob(snapshotFileInfoList);
        for (int i = 0; i < 4; i++) {
            ResponseEntity<Resource> resource =
                    this.snapshotsController.getDownloadFiles(response.getData(), false);
            if (resource.getStatusCode() == HttpStatus.OK) {
                return resource;
            }
            this.waitSec(5);
        }
        return this.snapshotsController.getDownloadFiles(response.getData(), false);
    }

    void backup() {
        // 手动触发 backup job
        ManualBackupRequest manualBackupRequest = new ManualBackupRequest();
        manualBackupRequest.setSyncFlowId(this.syncFlowEntity.getSyncFlowId().toString());
        this.snapshotsController.backup(manualBackupRequest);
        this.waitSec(5);
    }

    @BeforeEach
    void createFlowDefinition() {
        FlowNode node1 = new FlowNode(
                "1",
                "deduplicate",
                Map.of(FieldRegistry.SOURCE_DIRECTORY, new ParamValue(this.sourceFolderPath, ParamSourceType.MANUAL)),
                List.of("2")
        );
        FlowNode node2 = new FlowNode(
                "2",
                "backup",
                Map.of(
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.SOURCE_DIRECTORY, new ParamValue(this.sourceFolderPath, ParamSourceType.MANUAL)
                ),
                List.of()
        );
        this.nodeList = List.of(node1, node2);
        FieldSchemaDTO node1Schema = new FieldSchemaDTO(node1);
        node1Schema.addFieldSchema(
                FieldRegistry.SOURCE_DIRECTORY,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.SOURCE_DIRECTORY),
                this.sourceFolderPath
        );
        FieldSchemaDTO node2Schema = new FieldSchemaDTO(node2);
        node2Schema.addFieldSchema(
                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.RESTIC_BACKUP_REPOSITORY),
                this.backupPath
        );
        node2Schema.addFieldSchema(
                FieldRegistry.RESTIC_PASSWORD,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.RESTIC_PASSWORD),
                "0608"
        );
        node2Schema.addFieldSchema(
                FieldRegistry.SOURCE_DIRECTORY,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.SOURCE_DIRECTORY),
                this.sourceFolderPath
        );
        this.fieldSchemaDTOList = List.of(node1Schema, node2Schema);
    }

    void createSyncFlow(String filterCriteria) {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(contentFolderParentPath + "/random1");
        createSyncFlowRequest.setSyncFlowName("reactive_syncflow");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.REACTIVE_SYNC.getType());
        if (StringUtils.isNotBlank(filterCriteria)) {
            createSyncFlowRequest.setFilterCriteria(filterCriteria);
        }
        SyncDuoHttpResponse<SyncFlowInfo> syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert syncFlowResponse.getStatusCode() == 200;
        // 至多重试二次, 直到syncflow status 是 SYNC
        for (int i = 0; i < 2; i++) {
            waitAllFileHandle();
            this.syncFlowEntity = this.syncFlowService.getById(
                    Long.valueOf(syncFlowResponse.getData().getSyncFlowId())
            );
            if (this.syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name())) {
                break;
            }
        }
        assert this.syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.SYNC.name());
    }

    void createBackupOnlySyncFlow() {
        CreateSyncFlowRequest createSyncFlowRequest = new CreateSyncFlowRequest();
        createSyncFlowRequest.setSourceFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setDestFolderFullPath(sourceFolderPath);
        createSyncFlowRequest.setSyncFlowName("backup_only_syncflow");
        createSyncFlowRequest.setSyncFlowType(SyncFlowTypeEnum.BACKUP_ONLY.getType());
        SyncDuoHttpResponse<SyncFlowInfo> syncFlowResponse = this.syncFlowController.addSyncFlow(createSyncFlowRequest);
        assert syncFlowResponse.getStatusCode() == 200;
        this.syncFlowEntity = this.syncFlowService.getById(
                Long.valueOf(syncFlowResponse.getData().getSyncFlowId())
        );
        assert this.syncFlowEntity.getSyncStatus().equals(SyncFlowStatusEnum.BACKUP_ONLY_SYNC.name());
        assert this.syncFlowEntity.getSyncFlowType().equals(SyncFlowTypeEnum.BACKUP_ONLY.getType());
    }

    void waitAllFileHandle() {
        try {
            Thread.sleep(1000 * DELAY_UNIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void waitSingleFileHandle(String folderPath) throws SyncDuoException {
        this.folderWatcher.manualCheckFolder(folderPath);
        try {
            Thread.sleep(1000 * DELAY_UNIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void waitSec(long sec) throws SyncDuoException {
        try {
            Thread.sleep(sec * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @BeforeEach
    void prepareEnvironment() throws IOException, SyncDuoException {
        // 清空数据库
        this.truncateAllTable();
        // 清空文件夹
        this.deleteFolder();
        // 停止 watcher
        this.destroyStatefulComponent();
        // 创建 source folder
        FileOperationTestUtil.createFolders(
                sourceFolderPath,
                4,
                3
        );
        // 因为 spring boot 的 PostConstruct 方法在整个测试中只会执行一次
        // 而 deleteFolder 在每个测试方法前都会执行, 把 restic 的 backup folder 清空
        // 所以这里需要再执行一次 restic init
        this.resticFacadeService.init();
        log.info("initial finish");
    }

    void deleteFolder() throws IOException {
        // delete source folder
        FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(sourceFolderPath));
        // delete dest folder
        FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(contentFolderParentPath));
        // delete backup folder
        FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(backupPath));
        // delete restore folder
        FileOperationTestUtil.deleteAllFoldersLeaveItSelf(Path.of(restorePath));
        log.info("delete all folder");
    }

    void truncateAllTable() {
        // sync flow truncate
        this.syncFlowService.remove(new QueryWrapper<>());
        // copy job truncate
        this.copyJobService.remove(new QueryWrapper<>());
        // backup job truncate
        this.backupJobService.remove(new QueryWrapper<>());
        // restore job truncate
        this.restoreJobService.remove(new QueryWrapper<>());
        // flow definition truncate
        this.flowDefinitionMapper.delete(new QueryWrapper<>());
        // flow execution truncate
        this.flowExecutionMapper.delete(new QueryWrapper<>());
        // node execution truncate
        this.nodeExecutionMapper.delete(new QueryWrapper<>());
        // snapshot meta truncate
        this.snapshotMetaMapper.delete(new QueryWrapper<>());
        log.info("truncate all table");
    }

    void destroyStatefulComponent() {
        this.folderWatcher.destroy();
    }
}
