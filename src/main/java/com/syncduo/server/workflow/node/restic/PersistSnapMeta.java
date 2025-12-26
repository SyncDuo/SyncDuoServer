package com.syncduo.server.workflow.node.restic;

import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import com.syncduo.server.workflow.mapper.SnapshotMetaMapper;
import com.syncduo.server.workflow.model.db.SnapshotMetaEntity;
import com.syncduo.server.workflow.node.model.CommandResult;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import com.syncduo.server.workflow.node.restic.model.Snapshot;
import com.syncduo.server.workflow.node.restic.utils.ResticUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.BatchResult;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Node(
        name = "persist_snap_meta",
        description = "使用 restic command 获取 snapshot 的元数据, 存储到数据库",
        group = "restic",
        inputParams = {
                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                FieldRegistry.RESTIC_PASSWORD
        }
)
@Slf4j
@RequiredArgsConstructor
public class PersistSnapMeta extends BaseNode {
    private final SnapshotMetaMapper snapshotMetaMapper;

    @Override
    public NodeResult execute(FlowContext context) {
        String resticBackupRepository = FieldRegistry.getString(FieldRegistry.RESTIC_BACKUP_REPOSITORY, context);
        String resticPassword = FieldRegistry.getString(FieldRegistry.RESTIC_PASSWORD, context);
        if (StringUtils.isAnyBlank(resticBackupRepository, resticPassword)) {
            return NodeResult.failed("resticBackupRepository 或 resticPassword 为空");
        }
        // build snapshots command line
        CommandLine commandLine = new CommandLine("restic");
        commandLine.addArgument("--json");
        commandLine.addArgument("snapshots");
        CommandResult result = ResticUtil.execute(
                resticPassword,
                resticBackupRepository,
                commandLine
        );
        if (!result.isSuccess()) {
            return NodeResult.failed("restic 运行失败: " + result.getError());
        }
        String jsonOutput = result.getOutput();
        if (StringUtils.isBlank(jsonOutput)) {
            return NodeResult.success();
        }
        List<Snapshot> snapshots = JsonUtil.deserToList(jsonOutput, Snapshot.class);
        if (CollectionUtils.isEmpty(snapshots)) {
            return NodeResult.success();
        }
        // 找出不在 DB 的 snapshot id
        String snapshotIdsJson = JsonUtil.serializeToString(snapshots.stream().map(Snapshot::getId).toList());
        Set<String> missingSnapshotIds = this.snapshotMetaMapper.findMissingSnapshotIds(snapshotIdsJson);
        if (CollectionUtils.isEmpty(missingSnapshotIds)) {
            return NodeResult.success();
        }
        List<SnapshotMetaEntity> snapshotMetaEntityList = createFromSnapshot(
                snapshots.stream().filter(snapshot -> missingSnapshotIds.contains(snapshot.getId())).toList(),
                resticBackupRepository
        );
        List<BatchResult> insertResult = this.snapshotMetaMapper.insert(snapshotMetaEntityList);
        if (insertResult.size() != snapshotMetaEntityList.size()) {
            return NodeResult.failed("插入 db 失败");
        }
        return NodeResult.success();
    }

    private List<SnapshotMetaEntity> createFromSnapshot(List<Snapshot> snapshots, String backupRepository) {
        ArrayList<SnapshotMetaEntity> result = new ArrayList<>();
        for (Snapshot snapshot : snapshots) {
            SnapshotMetaEntity snapshotMetaEntity = new SnapshotMetaEntity()
                    .setSourceDirectory(snapshot.getPaths().get(0))
                    .setBackupRepository(backupRepository)
                    .setCreatedAt(Timestamp.from(snapshot.getTime().toInstant()))
                    .setSnapshotId(snapshot.getId())
                    .setFileCount(snapshot.getFileCount())
                    .setDirCount(snapshot.getDirCount())
                    .setSnapshotSizeBytes(snapshot.getTotalBytes())
                    .setHostname(snapshot.getHostname())
                    .setUsername(snapshot.getUsername());
            result.add(snapshotMetaEntity);
        }
        return result;
    }
}
