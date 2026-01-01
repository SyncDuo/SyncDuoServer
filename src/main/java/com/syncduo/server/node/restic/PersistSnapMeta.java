package com.syncduo.server.node.restic;

import com.syncduo.server.core.annotaion.Node;
import com.syncduo.server.core.model.base.BaseNode;
import com.syncduo.server.core.model.execution.FlowContext;
import com.syncduo.server.core.model.execution.NodeResult;
import com.syncduo.server.mapper.SnapshotMetaMapper;
import com.syncduo.server.model.db.SnapshotMetaEntity;
import com.syncduo.server.node.registry.FieldRegistry;
import com.syncduo.server.node.restic.model.Snapshot;
import com.syncduo.server.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.BatchResult;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Node(
        name = "persist_snap_meta",
        description = "使用 restic command 获取 snapshot 的元数据, 增量新增到数据库",
        group = "restic",
        inputParams = {
                FieldRegistry.RESTIC_SNAPSHOTS
        }
)
@Slf4j
@RequiredArgsConstructor
public class PersistSnapMeta extends BaseNode {
    private final SnapshotMetaMapper snapshotMetaMapper;

    @Override
    public NodeResult execute(FlowContext context) {
        String resticBackupRepository = FieldRegistry.getString(FieldRegistry.RESTIC_BACKUP_REPOSITORY, context);
        if (StringUtils.isAnyBlank(resticBackupRepository)) {
            return NodeResult.failed("resticBackupRepository 为空");
        }
        // 获取输出
        List<Snapshot> snapshots = FieldRegistry.getResticSnapshots(context);
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
