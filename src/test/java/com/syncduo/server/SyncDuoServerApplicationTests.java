package com.syncduo.server;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.controller.FileSystemAccessController;
import com.syncduo.server.controller.SystemInfoController;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.ResticExitCodeEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.api.filesystem.Folder;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import com.syncduo.server.model.api.systeminfo.SystemSettings;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.service.db.impl.BackupJobService;
import com.syncduo.server.service.db.impl.CopyJobService;
import com.syncduo.server.service.db.impl.RestoreJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.restic.ResticFacadeService;
import com.syncduo.server.workflow.controller.FlowEditorController;
import com.syncduo.server.workflow.controller.FlowInfoController;
import com.syncduo.server.workflow.controller.ResticController;
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
import com.syncduo.server.workflow.model.api.snapshot.RestoreRequest;
import com.syncduo.server.workflow.model.api.snapshot.SnapshotItemDTO;
import com.syncduo.server.workflow.model.db.FlowDefinitionEntity;
import com.syncduo.server.workflow.model.db.FlowExecutionEntity;
import com.syncduo.server.workflow.model.db.SnapshotMetaEntity;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;


@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.profiles.active=test")
@Slf4j
class SyncDuoServerApplicationTests {

    private final FlowEditorController flowEditorController;

    private final FlowInfoController flowInfoController;

    private final SyncFlowService syncFlowService;

    private final FolderWatcher folderWatcher;

    private final ResticFacadeService resticFacadeService;

    private final CopyJobService copyJobService;

    private final BackupJobService backupJobService;

    private final RestoreJobService restoreJobService;

    private final SystemInfoController systemInfoController;

    private final ResticController resticController;

    private final FileSystemAccessController fileSystemAccessController;

    private final FlowExecutionMapper flowExecutionMapper;

    private final NodeExecutionMapper nodeExecutionMapper;

    private final FlowDefinitionMapper flowDefinitionMapper;

    private final SnapshotMetaMapper snapshotMetaMapper;

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

    @Autowired
    public SyncDuoServerApplicationTests(
            FlowEditorController flowEditorController,
            FlowInfoController flowInfoController,
            SyncFlowService syncFlowService,
            FolderWatcher folderWatcher,
            ResticFacadeService resticFacadeService,
            CopyJobService copyJobService,
            BackupJobService backupJobService,
            RestoreJobService restoreJobService,
            SystemInfoController systemInfoController,
            ResticController resticController,
            FileSystemAccessController fileSystemAccessController,
            FlowExecutionMapper flowExecutionMapper,
            NodeExecutionMapper nodeExecutionMapper,
            FlowDefinitionMapper flowDefinitionMapper,
            SnapshotMetaMapper snapshotMetaMapper,
            FlowEngine flowEngine) {
        this.flowEditorController = flowEditorController;
        this.flowInfoController = flowInfoController;
        this.syncFlowService = syncFlowService;
        this.folderWatcher = folderWatcher;
        this.resticFacadeService = resticFacadeService;
        this.copyJobService = copyJobService;
        this.backupJobService = backupJobService;
        this.restoreJobService = restoreJobService;
        this.systemInfoController = systemInfoController;
        this.resticController = resticController;
        this.fileSystemAccessController = fileSystemAccessController;
        this.flowExecutionMapper = flowExecutionMapper;
        this.nodeExecutionMapper = nodeExecutionMapper;
        this.flowDefinitionMapper = flowDefinitionMapper;
        this.snapshotMetaMapper = snapshotMetaMapper;
        this.flowEngine = flowEngine;
    }

    @Test
    void GetAllSnapshotsTest() throws IOException {
        this.CreateDataInDB();
        FlowResponse<List<SnapshotMetaEntity>> result = this.resticController.getAllSnapshot();
        Assertions.assertEquals(200, result.getStatusCode());
        assertTrue(CollectionUtils.isNotEmpty(result.getData()));
        FlowResponse<List<SnapshotItemDTO>> ls = this.resticController.getSnapshotItem(result.getData().get(0), "/");
        Assertions.assertEquals(200, ls.getStatusCode());
        assertTrue(CollectionUtils.isNotEmpty(ls.getData()));
        ResponseEntity<Resource> responseEntity = this.resticController.download(
                new RestoreRequest(
                        result.getData().get(0),
                        ls.getData()
                ),
                false
        );
        assertEquals(200, responseEntity.getStatusCode().value());
        ContentDisposition contentDisposition = responseEntity.getHeaders().getContentDisposition();
        assertNotNull(contentDisposition);
        assertTrue(contentDisposition.isAttachment());
        assertNotNull(contentDisposition.getFilename());
        assertTrue(contentDisposition.getFilename().contains("zip"));
        assertNotNull(responseEntity.getBody());
        assertTrue(responseEntity.getBody().contentLength() > 0);
    }

    @Test
    void RestoreNodeTest() {
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
                "fetch_snapshots",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                List.of("3")
        );
        FlowNode node3 = new FlowNode(
                "3",
                "persist_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_SNAPSHOTS, new ParamValue(null, ParamSourceType.NODE_OUTPUT)
                ),
                List.of()
        );
        this.flowEngine.execute(1L, new FlowDefinition("tmp", List.of(node1, node2, node3)));
        waitSec(20);
        String snapshotId = this.snapshotMetaMapper.selectList(new LambdaQueryWrapper<>()).get(0).getSnapshotId();
        FlowNode node4 = new FlowNode(
                "1",
                "fetch_snapshot_item",
                Map.of(
                        FieldRegistry.RESTIC_LS_FILTER, new ParamValue("/TestFolder1_1/TestFile1_1.bin", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_SNAPSHOT_ID, new ParamValue(snapshotId, ParamSourceType.MANUAL)
                ),
                List.of()
        );
        FlowNode node5 = new FlowNode(
                "2",
                "restore",
                Map.of(
                        FieldRegistry.RESTIC_SNAPSHOT_ID, new ParamValue(snapshotId, ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_SNAPSHOT_NODES, new ParamValue(null, ParamSourceType.NODE_OUTPUT),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL)
                ),
                List.of()
        );
        this.flowEngine.execute(1L, new FlowDefinition("tmp", List.of(node4, node5)));
        waitSec(10);
    }

    @Test
    void FetchSnapshotItemNodeTest() {
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
                "fetch_snapshots",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                List.of("3")
        );
        FlowNode node3 = new FlowNode(
                "3",
                "persist_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_SNAPSHOTS, new ParamValue(null, ParamSourceType.NODE_OUTPUT)
                ),
                List.of()
        );
        this.flowEngine.execute(1L, new FlowDefinition("tmp", List.of(node1, node2, node3)));
        waitSec(20);
        String snapshotId = this.snapshotMetaMapper.selectList(new LambdaQueryWrapper<>()).get(0).getSnapshotId();
        FlowNode node4 = new FlowNode(
                "1",
                "fetch_snapshot_item",
                Map.of(
                        FieldRegistry.RESTIC_LS_FILTER, new ParamValue("/", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_SNAPSHOT_ID, new ParamValue(snapshotId, ParamSourceType.MANUAL)
                ),
                List.of()
        );
        this.flowEngine.execute(1L, new FlowDefinition("tmp", List.of(node4)));
        waitSec(15);
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
    void DeleteSnapMetaNodeTest() {
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
                "fetch_snapshots",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                List.of("3")
        );
        FlowNode node3 = new FlowNode(
                "3",
                "persist_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_SNAPSHOTS, new ParamValue(null, ParamSourceType.NODE_OUTPUT)
                ),
                List.of("4")
        );
        // 注意, 需要在 fetch_snapshots node 执行前手动删除 snapshots
        FlowNode node4 = new FlowNode(
                "4",
                "fetch_snapshots",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                List.of("5")
        );
        FlowNode node5 = new FlowNode(
                "5",
                "delete_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_SNAPSHOTS, new ParamValue(null, ParamSourceType.NODE_OUTPUT)
                ),
                Collections.emptyList()
        );
        FlowDefinition tmp = new FlowDefinition("tmp", List.of(node1, node2, node3, node4, node5));
        this.flowEngine.execute(1L, tmp);
        waitSec(60);
        List<SnapshotMetaEntity> allResult = this.snapshotMetaMapper.selectList(new QueryWrapper<>());
        assertEquals(0,
                allResult.stream()
                        .filter(n -> n.getRecordDeleted().equals(DeletedEnum.DELETED.getCode()))
                        .toList()
                        .size()
        );
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
                "fetch_snapshots",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                List.of("3")
        );
        FlowNode node3 = new FlowNode(
                "3",
                "persist_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_SNAPSHOTS, new ParamValue(null, ParamSourceType.NODE_OUTPUT)
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
    void CreateDataInDB() throws IOException {
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
                waitSec(10);
            }
            FileOperationTestUtil.modifyFile(Path.of(this.sourceFolderPath), i+1);
        }
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
                List.of("3")
        );
        FlowNode node3 = new FlowNode(
                "3",
                "fetch_snapshots",
                Map.of(
                        FieldRegistry.RESTIC_PASSWORD, new ParamValue("0608", ParamSourceType.MANUAL),
                        FieldRegistry.RESTIC_BACKUP_REPOSITORY, new ParamValue(this.backupPath, ParamSourceType.MANUAL)
                ),
                List.of("4")
        );
        FlowNode node4 = new FlowNode(
                "4",
                "persist_snap_meta",
                Map.of(
                        FieldRegistry.RESTIC_SNAPSHOTS, new ParamValue(null, ParamSourceType.NODE_OUTPUT)
                ),
                Collections.emptyList()
        );
        this.nodeList = List.of(node1, node2, node3, node4);
        // node 1 schema
        FieldSchemaDTO node1Schema = new FieldSchemaDTO(node1);
        node1Schema.addFieldSchema(
                FieldRegistry.SOURCE_DIRECTORY,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.SOURCE_DIRECTORY),
                this.sourceFolderPath
        );
        // node 2 schema
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
        // node 3 schema
        FieldSchemaDTO node3Schema = new FieldSchemaDTO(node3);
        node3Schema.addFieldSchema(
                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.RESTIC_BACKUP_REPOSITORY),
                this.backupPath
        );
        node3Schema.addFieldSchema(
                FieldRegistry.RESTIC_PASSWORD,
                ParamSourceType.MANUAL.name(),
                FieldRegistry.getMeta(FieldRegistry.RESTIC_PASSWORD),
                "0608"
        );
        // node 4 schema
        FieldSchemaDTO node4Schema = new FieldSchemaDTO(node4);
        node4Schema.addFieldSchema(
                FieldRegistry.RESTIC_SNAPSHOTS,
                ParamSourceType.NODE_OUTPUT.name(),
                FieldRegistry.getMeta(FieldRegistry.RESTIC_SNAPSHOTS)
        );
        this.fieldSchemaDTOList = List.of(node1Schema, node2Schema, node3Schema, node4Schema);
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
        FileOperationTestUtil.createFolders(sourceFolderPath, 4, 3);
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
        // delete temp folder(start with "restore")
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
