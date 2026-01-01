package com.syncduo.server.workflow.node.restic.utils;

import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.workflow.node.model.CommandResult;
import com.syncduo.server.workflow.node.restic.enums.ResticExitCode;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.util.validation.ValidationException;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class ResticUtil {

    public static CommandResult execute(
            String resticPassword,
            String resticRepository,
            CommandLine commandLine
    ) throws ValidationException {
        return execute(resticPassword, resticRepository, null, commandLine);
    }

    public static CommandResult execute(
            String resticPassword,
            String resticRepository,
            String workingDirectory,
            CommandLine commandLine
            ) throws ValidationException {
        if (StringUtils.isAnyBlank(resticPassword, resticRepository)) {
            throw new ValidationException("restic execute failed. resticPassWord or resticRepository is null");
        }
        if (!isInitialized(resticPassword, resticRepository)) {
            initRepository(resticPassword, resticRepository);
        }
        Executor executor = DefaultExecutor.builder().get();
        // 1. 工作目录
        if (StringUtils.isNotBlank(workingDirectory)) {
            executor.setWorkingDirectory(new File(workingDirectory));
        }
        // 2. 创建输出流处理器（捕获 stdout/stderr）
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        executor.setStreamHandler(new PumpStreamHandler(stdout, stderr));
        // 3. 设置超时. 计算公式 = 100G 文件备份 / 30MB/s 机械硬盘速度 / 60 ~= 60 分钟
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofMinutes(60L)).get();
        executor.setWatchdog(watchdog);
        try {
            // 4. 阻塞执行
            int exitCode = executor.execute(commandLine, genResticEnv(resticPassword, resticRepository));
            if (exitCode == ResticExitCode.SUCCESS.getCode()) {
                return CommandResult.success(exitCode, getStdAsString(stdout));
            } else {
                return CommandResult.failed(exitCode, getStdAsString(stderr));
            }
        } catch (Exception e) {
            return CommandResult.failed(
                    ResticExitCode.ERROR_WITHOUT_EXITCODE.getCode(),
                    "exception: %s with stderr: %s".formatted(e, getStdAsString(stderr))
            );
        }
    }

    private static void initRepository(String resticPassword, String resticRepository) {
        // build command line
        DefaultExecutor executor = DefaultExecutor.builder().get();
        try {
            int exitCode = executor.execute(
                    CommandLine.parse("restic init --json"),
                    genResticEnv(resticPassword, resticRepository)
            );
            ResticExitCode resticExitCode = ResticExitCode.fromCode(exitCode);
            if (resticExitCode != ResticExitCode.SUCCESS) {
                throw new BusinessException("restic init 命令返回未处理 exitCode %s".formatted(exitCode));
            }
        } catch (IOException e) {
            throw new BusinessException("restic init 命令执行失败", e);
        }
    }

    private static boolean isInitialized(String resticPassword, String resticRepository) {
        // build command line
        DefaultExecutor executor = DefaultExecutor.builder().get();
        try {
            int exitCode = executor.execute(
                    CommandLine.parse("restic cat config --json"),
                    genResticEnv(resticPassword, resticRepository)
            );
            if (exitCode == ResticExitCode.SUCCESS.getCode()) {
                return true;
            } else if (exitCode == ResticExitCode.REPOSITORY_NOT_FOUND.getCode()) {
                return false;
            } else {
                throw new BusinessException("restic cat config 命令返回未处理退出码 %s".formatted(exitCode));
            }
        } catch (IOException e) {
            throw new BusinessException("restic cat config 命令执行失败", e);
        }
    }

    private static String getStdAsString(ByteArrayOutputStream std) {
        if (ObjectUtils.isEmpty(std)) {
            return "";
        }
        return std.toString(StandardCharsets.UTF_8).trim();
    }

    private static Map<String, String> genResticEnv(String resticPassword, String resticRepository) {
        // 获取当前系统变量
        Map<String, String> result = new HashMap<>(System.getenv());
        // 设置 RESTIC 密码和备份目录
        result.put("RESTIC_PASSWORD", resticPassword);
        result.put("RESTIC_REPOSITORY", resticRepository);
        return result;
    }
}
