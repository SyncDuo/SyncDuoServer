package com.syncduo.server.node.restic;

import com.syncduo.server.core.annotaion.Node;
import com.syncduo.server.core.model.base.BaseNode;
import com.syncduo.server.core.model.execution.FlowContext;
import com.syncduo.server.core.model.execution.NodeResult;
import com.syncduo.server.node.model.CommandResult;
import com.syncduo.server.node.registry.FieldRegistry;
import com.syncduo.server.node.restic.model.SnapshotNode;
import com.syncduo.server.node.restic.utils.ResticUtil;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Node(
        name = "restore",
        description = "使用 restic restore 命令从 snapshot 中恢复",
        group = "restic",
        inputParams = {
                FieldRegistry.RESTIC_SNAPSHOT_ID,
                FieldRegistry.RESTIC_SNAPSHOT_NODES,
                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                FieldRegistry.RESTIC_PASSWORD
        },
        outputParams = {
                FieldRegistry.RESTIC_RESTORE_RESULT
        }
)
@Slf4j
public class Restore extends BaseNode {
    @Override
    public NodeResult execute(FlowContext context) {
        String snapshotId = FieldRegistry.getString(FieldRegistry.RESTIC_SNAPSHOT_ID, context);
        String repository = FieldRegistry.getString(FieldRegistry.RESTIC_BACKUP_REPOSITORY, context);
        String password = FieldRegistry.getString(FieldRegistry.RESTIC_PASSWORD, context);
        if (StringUtils.isAnyBlank(snapshotId, repository, password)) {
            return NodeResult.failed("snapshotId, repository, password is null");
        }
        List<SnapshotNode> snapshotNodes = FieldRegistry.getValue(FieldRegistry.RESTIC_SNAPSHOT_NODES, context);
        if (CollectionUtils.isEmpty(snapshotNodes)) {
            return NodeResult.failed("snapshot items is null");
        }
        // build command line
        Path tempDirectory;
        try {
            tempDirectory = Files.createTempDirectory("restore" + "-" + UUID.randomUUID());
        } catch (IOException e) {
            return NodeResult.failed(e.getMessage());
        }
        CommandLine commandLine = snapshotNodes.size() == 1 ?
                this.buildCommandLine(snapshotId, snapshotNodes.get(0), tempDirectory) :
                this.buildCommandLine(snapshotId, snapshotNodes, tempDirectory);
        CommandResult commandResult = ResticUtil.execute(password, repository, commandLine);
        if (!commandResult.isSuccess()) {
            return NodeResult.failed(commandResult.getError());
        }
        // pack files and dirs
        if (snapshotNodes.size() == 1 && snapshotNodes.get(0).isFile()) {
            File file = FileUtils.getFile(tempDirectory.toFile(), snapshotNodes.get(0).getName());
            return NodeResult.success(Map.of(
                    FieldRegistry.RESTIC_RESTORE_RESULT, file.toPath()
            ));
        } else {
            Path zipFile = FilesystemUtil.zipAllFile(tempDirectory.toAbsolutePath().toString(), "restore");
            return NodeResult.success(Map.of(
                    FieldRegistry.RESTIC_RESTORE_RESULT, zipFile
            ));
        }
    }

    private CommandLine buildCommandLine(String snapshotId, SnapshotNode snapshotNode, Path tempDir) {
        CommandLine commandLine = new CommandLine("restic");
        commandLine.addArgument("restore");
        commandLine.addArgument(snapshotId + ":" + snapshotNode.getParentPath());
        commandLine.addArgument("--target");
        commandLine.addArgument(tempDir.toAbsolutePath().toString());
        commandLine.addArgument("--include");
        commandLine.addArgument("/" + snapshotNode.getName());
        return commandLine;
    }

    private CommandLine buildCommandLine(String snapshotId, List<SnapshotNode> snapshotNodes, Path tempDir) {
        CommandLine commandLine = new CommandLine("restic");
        commandLine.addArgument("restore");
        commandLine.addArgument(snapshotId);
        commandLine.addArgument("--target");
        commandLine.addArgument(tempDir.toAbsolutePath().toString());
        for (SnapshotNode snapshotNode : snapshotNodes) {
            commandLine.addArgument("--include");
            commandLine.addArgument("/" + snapshotNode.getName());
        }
        return commandLine;
    }
}
