package com.syncduo.server.workflow.node.rclone.utils;

import com.syncduo.server.workflow.node.model.CommandResult;
import com.syncduo.server.workflow.node.rclone.enums.RcloneExitCode;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.util.validation.ValidationException;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.ObjectUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
public class RcloneUtil {
    public static CommandResult execute(CommandLine commandLine, boolean parseErr)
            throws ValidationException {
        Executor executor = DefaultExecutor.builder().get();
        // 1. 创建输出流处理器（捕获 stdout/stderr）
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        // 2. 设置超时. 计算公式 = 100G 文件同步 / 30MB/s 机械硬盘速度 / 60 ~= 60 分钟
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofMinutes(60L)).get();
        executor.setWatchdog(watchdog);
        try {
            // 3. 阻塞执行
            int exitCode = executor.execute(commandLine);
            RcloneExitCode rcloneExitCode = RcloneExitCode.fromCode(exitCode);
            if (rcloneExitCode.isSuccess()) {
                return CommandResult.success(exitCode, getStdAsString(parseErr ? stderr : stdout));
            } else {
                return CommandResult.failed(exitCode, getStdAsString(stderr));
            }
        } catch (Exception e) {
            return CommandResult.failed(
                    RcloneExitCode.ERROR_WITHOUT_EXITCODE.getCode(),
                    "exception: %s with stderr: %s".formatted(e, getStdAsString(stderr))
            );
        }
    }

    private static String getStdAsString(ByteArrayOutputStream std) {
        if (ObjectUtils.isEmpty(std)) {
            return "";
        }
        return std.toString(StandardCharsets.UTF_8).trim();
    }
}
