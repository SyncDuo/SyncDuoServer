package com.syncduo.server.workflow.node.restic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.exception.DbException;
import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import com.syncduo.server.workflow.mapper.SnapshotMetaMapper;
import com.syncduo.server.workflow.model.db.SnapshotMetaEntity;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import com.syncduo.server.workflow.node.restic.model.Snapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.ibatis.executor.BatchResult;

import java.util.List;

@Node(
        name = "delete_snap_meta",
        description = "使用 restic command 获取 snapshot 的元数据, 从数据库中删除不在 restic 的snapshot",
        group = "restic",
        inputParams = {
                FieldRegistry.RESTIC_SNAPSHOTS
        }
)
@Slf4j
@RequiredArgsConstructor
public class DeleteSnapMeta extends BaseNode {
    private final SnapshotMetaMapper snapshotMetaMapper;

    @Override
    public NodeResult execute(FlowContext context) {
        // 获取输出
        List<Snapshot> snapshots = FieldRegistry.getResticSnapshots(context);
        if (CollectionUtils.isEmpty(snapshots)) {
            // 删除所有记录
            this.deleteAllRecord();
            return NodeResult.success();
        }
        // 找出不在 DB 的 snapshot id
        String snapshotIdsJson = JsonUtil.serializeToString(snapshots.stream().map(Snapshot::getId).toList());
        List<SnapshotMetaEntity> deletedSnapshotMetaEntity =
                this.snapshotMetaMapper.findDeletedSnapshotMeta(snapshotIdsJson);
        if (CollectionUtils.isEmpty(deletedSnapshotMetaEntity)) {
            return NodeResult.success();
        }
        deletedSnapshotMetaEntity.forEach(n -> n.setRecordDeleted(DeletedEnum.DELETED.getCode()));
        List<BatchResult> updateResult = this.snapshotMetaMapper.updateById(deletedSnapshotMetaEntity);
        if (updateResult.size() != deletedSnapshotMetaEntity.size()) {
            return NodeResult.failed("更新 db 失败");
        }
        return NodeResult.success();
    }

    private void deleteAllRecord() {
        LambdaQueryWrapper<SnapshotMetaEntity> queryWrapper = new LambdaQueryWrapper<>();
        List<SnapshotMetaEntity> dbResult = this.snapshotMetaMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(dbResult)) {
            return;
        }
        dbResult.forEach(n -> n.setRecordDeleted(DeletedEnum.DELETED.getCode()));
        List<BatchResult> batchResults = this.snapshotMetaMapper.updateById(dbResult);
        if (batchResults.size() != dbResult.size()) {
            throw new DbException("更新 DB 失败");
        }
    }
}
