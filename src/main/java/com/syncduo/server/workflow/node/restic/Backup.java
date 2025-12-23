package com.syncduo.server.workflow.node.restic;

import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import com.syncduo.server.workflow.node.restic.model.ResticCommandResult;
import com.syncduo.server.workflow.node.restic.utils.ResticUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Node(
        name = "backup",
        description = "备份",
        group = "restic",
        inputParams = {
                FieldRegistry.SOURCE_DIRECTORY,
                FieldRegistry.RESTIC_BACKUP_REPOSITORY,
                FieldRegistry.RESTIC_PASSWORD
        },
        outputParams = {FieldRegistry.RESTIC_BACKUP_RESULT}
)
@Slf4j
public class Backup extends BaseNode {
    @Override
    public NodeResult execute(FlowContext context) {
        // 获取参数
        String sourceDirectory = FieldRegistry.getString(FieldRegistry.SOURCE_DIRECTORY, context);
        String resticBackupRepository = FieldRegistry.getString(FieldRegistry.RESTIC_BACKUP_REPOSITORY, context);
        String resticPassword = FieldRegistry.getString(FieldRegistry.RESTIC_PASSWORD, context);
        // 参数校验
        FilesystemUtil.isFolderPathValid(sourceDirectory);
        FilesystemUtil.isFolderPathValid(resticBackupRepository);
        if (StringUtils.isBlank(resticPassword)) {
            throw new BusinessException("empty restic password");
        }
        // 生成 command line
        CommandLine commandLine = new CommandLine("restic");
        commandLine.addArgument("--json");
        commandLine.addArgument("backup");
        commandLine.addArgument(".");
        commandLine.addArgument("--skip-if-unchanged");
        // 执行备份
        ResticCommandResult result = ResticUtil.execute(
                resticPassword,
                resticBackupRepository,
                sourceDirectory,
                commandLine
        );
        if (result.isSuccess()) {
            // 只保留 "summary" 对象. https://restic.readthedocs.io/en/stable/075_scripting.html#backup
            String summary = JsonUtil.getResticJsonLinesByMsgType(result.getJsonOutput(), "summary").get(0);
            result.setJsonOutput(summary);
            return NodeResult.success(Map.of(FieldRegistry.RESTIC_BACKUP_RESULT, result));
        } else {
            return NodeResult.failed(result.getErrorOutput());
        }
    }
}
