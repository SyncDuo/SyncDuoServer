package com.syncduo.server.workflow.node.rclone;

import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import com.syncduo.server.workflow.node.model.CommandResult;
import com.syncduo.server.workflow.node.rclone.model.CopyResult;
import com.syncduo.server.workflow.node.rclone.utils.RcloneUtil;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Node(
        name = "oneway_sync",
        description = "使用 rclone, 单向同步 src 到 dst",
        group = "rclone",
        inputParams = {
                FieldRegistry.SOURCE_DIRECTORY,
                FieldRegistry.DST_DIRECTORY
        },
        outputParams = {
                FieldRegistry.RCLONE_COPY_RESULT
        }
)
@Slf4j
public class Copy extends BaseNode {
        @Override
        public NodeResult execute(FlowContext context) {
                String srcDir = FieldRegistry.getString(FieldRegistry.SOURCE_DIRECTORY, context);
                String dstDir = FieldRegistry.getString(FieldRegistry.DST_DIRECTORY, context);
                if (StringUtils.isAnyBlank(srcDir, dstDir)) {
                        return NodeResult.failed("srcDir or dstDir is blank");
                }
                // build command line
                CommandLine commandLine = getCopyCommandLine(srcDir, dstDir);
                // 命令执行
                CommandResult commandResult = RcloneUtil.execute(commandLine, true);
                if (commandResult.isSuccess()) {
                        CopyResult copyResult = JsonUtil.readLastLine(commandResult.getOutput(), CopyResult.class);
                        if (ObjectUtils.isEmpty(copyResult)) {
                                return NodeResult.failed(("无法反序列化 rclone copy 的结果. " +
                                        "输出是 %s").formatted(commandResult.getOutput()));
                        }
                        return NodeResult.success(Map.of(FieldRegistry.RCLONE_COPY_RESULT, copyResult));
                } else {
                        return NodeResult.failed(commandResult.getError());
                }
        }

        private static CommandLine getCopyCommandLine(String srcDir, String dstDir) {
                CommandLine commandLine = new CommandLine("rclone");
                commandLine.addArgument("copy");
                commandLine.addArgument(srcDir);
                commandLine.addArgument(dstDir);
                commandLine.addArgument("--use-json-log");
                commandLine.addArgument("--stats-log-level");
                commandLine.addArgument("NOTICE");
                commandLine.addArgument("--log-level");
                commandLine.addArgument("NOTICE");
                commandLine.addArgument("--stats-one-line");
                commandLine.addArgument("--error-on-no-transfer");
                return commandLine;
        }
}
