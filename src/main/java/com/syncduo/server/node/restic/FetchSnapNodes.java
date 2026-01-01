package com.syncduo.server.node.restic;

import com.syncduo.server.core.annotaion.Node;
import com.syncduo.server.core.model.base.BaseNode;
import com.syncduo.server.core.model.execution.FlowContext;
import com.syncduo.server.core.model.execution.NodeResult;
import com.syncduo.server.node.model.CommandResult;
import com.syncduo.server.node.registry.FieldRegistry;
import com.syncduo.server.node.restic.model.SnapshotNode;
import com.syncduo.server.node.restic.utils.ResticUtil;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Node(
        name = "fetch_snapshot_nodes",
        description = "使用 restic ls 命令获取 snapshot 内的文件夹和文件路径",
        group = "restic",
        inputParams = {
                FieldRegistry.RESTIC_SNAPSHOT_ID,
                FieldRegistry.RESTIC_LS_FILTER,
                FieldRegistry.RESTIC_PASSWORD,
                FieldRegistry.RESTIC_BACKUP_REPOSITORY
        },
        outputParams = {
                FieldRegistry.RESTIC_SNAPSHOT_NODES
        }
)
@Slf4j
public class FetchSnapNodes extends BaseNode {
    @Override
    public NodeResult execute(FlowContext context) {
        String snapshotId = FieldRegistry.getString(FieldRegistry.RESTIC_SNAPSHOT_ID, context);
        String password = FieldRegistry.getString(FieldRegistry.RESTIC_PASSWORD, context);
        String repository = FieldRegistry.getString(FieldRegistry.RESTIC_BACKUP_REPOSITORY, context);
        String filter = FieldRegistry.getString(FieldRegistry.RESTIC_LS_FILTER, context);
        if (StringUtils.isAnyBlank(snapshotId, password, repository, filter)) {
            return NodeResult.failed("snapshotId, password, repository id, filter is null");
        }
        // build command line
        CommandLine commandLine = new CommandLine("restic");
        commandLine.addArgument("ls");
        commandLine.addArgument(snapshotId);
        commandLine.addArgument(filter);
        commandLine.addArgument("--json");
        CommandResult commandResult = ResticUtil.execute(password, repository, commandLine);
        if (!commandResult.isSuccess()) {
            return NodeResult.failed(commandResult.getError());
        }
        List<SnapshotNode> snapshotNodes = JsonUtil.aggResticOutputByMsgType(
                commandResult.getOutput(),
                "node",
                SnapshotNode.class
        );
        return CollectionUtils.isEmpty(snapshotNodes) ?
                NodeResult.success() :
                NodeResult.success(Map.of(FieldRegistry.RESTIC_SNAPSHOT_NODES, snapshotNodes));
    }
}
