package com.syncduo.server.workflow.node.restic;

import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import com.syncduo.server.workflow.node.model.CommandResult;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import com.syncduo.server.workflow.node.restic.model.SnapshotItem;
import com.syncduo.server.workflow.node.restic.utils.ResticUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

@Node(
        name = "fetch_snapshot_item",
        description = "使用 restic ls 命令获取 snapshot 内的文件夹和文件路径",
        group = "restic",
        inputParams = {
                FieldRegistry.RESTIC_SNAPSHOT_ID,
                FieldRegistry.RESTIC_LS_FILTER,
                FieldRegistry.RESTIC_PASSWORD,
                FieldRegistry.RESTIC_BACKUP_REPOSITORY
        },
        outputParams = {
                FieldRegistry.RESTIC_SNAPSHOT_ITEMS
        }
)
@Slf4j
public class FetchSnapItem extends BaseNode {
    @Override
    public NodeResult execute(FlowContext context) {
        String snapshotId = FieldRegistry.getString(FieldRegistry.RESTIC_SNAPSHOT_ID, context);
        String password = FieldRegistry.getString(FieldRegistry.RESTIC_PASSWORD, context);
        String repository = FieldRegistry.getString(FieldRegistry.RESTIC_BACKUP_REPOSITORY, context);
        if (StringUtils.isAnyBlank(snapshotId, password, repository)) {
            return NodeResult.failed("snapshotId, password, repository id is null");
        }
        String filter = FieldRegistry.getString(FieldRegistry.RESTIC_LS_FILTER, context);
        filter = StringUtils.isBlank(filter) ? "/" : filter;
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
        List<SnapshotItem> snapshotItems = JsonUtil.parseResticJsonLines(
                commandResult.getOutput(),
                "node",
                SnapshotItem.class
        );
        return CollectionUtils.isEmpty(snapshotItems) ?
                NodeResult.success() :
                NodeResult.success(Map.of(FieldRegistry.RESTIC_SNAPSHOT_ITEMS, snapshotItems));
    }
}
