package com.syncduo.server.workflow.node.restic;

import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import com.syncduo.server.workflow.node.model.CommandResult;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import com.syncduo.server.workflow.node.restic.model.Snapshot;
import com.syncduo.server.workflow.node.restic.utils.ResticUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Node(
        name = "fetch_snapshots",
        description = "使用 restic command 获取 snapshot 的元数据",
        group = "restic",
        inputParams = {
                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                FieldRegistry.RESTIC_PASSWORD
        },
        outputParams = {
                FieldRegistry.RESTIC_SNAPSHOTS
        }
)
@Slf4j
public class FetchSnapshots extends BaseNode {
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
            return NodeResult.success(
                    Map.of(FieldRegistry.RESTIC_SNAPSHOTS, new ArrayList<List<Snapshot>>())
            );
        }
        List<Snapshot> snapshots = JsonUtil.deserToList(jsonOutput, Snapshot.class);
        return NodeResult.success(
                Map.of(FieldRegistry.RESTIC_SNAPSHOTS, snapshots)
        );
    }
}
