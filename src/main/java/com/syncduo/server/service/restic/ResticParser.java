package com.syncduo.server.service.restic;

import com.syncduo.server.enums.ResticExitCodeEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.model.restic.global.ExitErrors;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.util.validation.ValidationException;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Slf4j
public class ResticParser {
    public static <SR>  CompletableFuture<ResticExecResult<SR, ExitErrors>> executeWithExitErrorsHandler(
            String resticPassword,
            String resticRepository,
            CommandLine commandLine,
            Function<String, SR> successHandler
    ) throws com.syncduo.server.exception.ValidationException {
        return execute(
                resticPassword,
                resticRepository,
                null,
                null,
                commandLine,
                successHandler,
                stderr -> JsonUtil.parseResticJsonDocument(stderr, ExitErrors.class)
        );
    }

    public static <SR, FR>  CompletableFuture<ResticExecResult<SR, FR>> executeWithWorkingDirectory(
            String resticPassword,
            String resticRepository,
            String workingDirectory,
            CommandLine commandLine,
            Function<String, SR> successHandler,
            Function<String, FR> failedHandler
    ) throws ValidationException {
        return execute(
                resticPassword,
                resticRepository,
                null,
                workingDirectory,
                commandLine,
                successHandler,
                failedHandler
        );
    }

    public static <SR, FR>  CompletableFuture<ResticExecResult<SR, FR>> execute(
            String resticPassword,
            String resticRepository,
            Map<String, String> extraEnvMap,
            String workingDirectory,
            CommandLine commandLine,
            Function<String, SR> successHandler,
            Function<String, FR> failedHandler
            ) throws ValidationException {
        // 检查参数
        if (ObjectUtils.anyNull(commandLine, successHandler, failedHandler)) {
            throw new ValidationException("restic execute failed. " +
                    "commandLine, successHandler or failedHandler is null");
        }
        if (StringUtils.isAnyBlank(resticPassword, resticRepository)) {
            throw new ValidationException("restic execute failed. resticPassWord or resticRepository is null");
        }
        Executor executor = DefaultExecutor.builder().get();
        CompletableFuture<ResticExecResult<SR, FR>> future = new CompletableFuture<>();
        // 1. 工作目录
        if (StringUtils.isNotBlank(workingDirectory)) {
            executor.setWorkingDirectory(new File(workingDirectory));
        }
        // 2. 创建输出流处理器（捕获 stdout/stderr）
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr);
        executor.setStreamHandler(streamHandler);
        // 3. 设置超时
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofSeconds(60)).get();
        executor.setWatchdog(watchdog);
        // 4. 非阻塞执行
        Map<String, String> env = getEnv(resticPassword, resticRepository, extraEnvMap);
        try {
            executor.execute(commandLine, env, new ExecuteResultHandler() {
                @Override
                public void onProcessComplete(int exitCode) {
                    ResticExitCodeEnum resticExitCodeEnum = ResticExitCodeEnum.fromCode(exitCode);
                    String stdoutString = getStdAsString(stdout);
                    try {
                        SR successResult = successHandler.apply(stdoutString);
                        future.complete(ResticExecResult.success(
                                resticExitCodeEnum,
                                successResult
                        ));
                    } catch (Exception e) {
                        future.complete(ResticExecResult.failed(
                                resticExitCodeEnum,
                                new BusinessException("restic command success." +
                                        "stdout is %s. but handler failed.".formatted(stdoutString), e)));
                    }
                }

                @Override
                public void onProcessFailed(ExecuteException e) {
                    int exitValue = e.getExitValue();
                    ResticExitCodeEnum resticExitCodeEnum = ResticExitCodeEnum.fromCode(exitValue);
                    String stderrString = getStdAsString(stderr);
                    try {
                        FR failedResult = failedHandler.apply(stderrString);
                        future.complete(ResticExecResult.failed(
                                resticExitCodeEnum,
                                failedResult
                        ));
                    } catch (Exception ex) {
                        // 使用 SyncDuoException 包装 stderr 返回
                        future.complete(ResticExecResult.failed(
                                resticExitCodeEnum,
                                new BusinessException("restic failed handler failed." +
                                        "stderr is %s.".formatted(stderrString), e)));
                    }
                }
            });
        } catch (Exception e) {
            future.complete(ResticExecResult.failed(
                    new BusinessException("restic failed before command exec. ", e)));
        }
        return future;
    }

    private static String getStdAsString(ByteArrayOutputStream std) {
        if (ObjectUtils.isEmpty(std)) {
            return "";
        }
        return std.toString(StandardCharsets.UTF_8).trim();
    }

    private static Map<String, String> getEnv(
            String resticPassword,
            String resticRepository,
            Map<String, String> extraEnvMap) {
        // 获取当前系统变量
        Map<String, String> result = new HashMap<>(System.getenv());
        // 设置 RESTIC 密码和备份目录
        result.put("RESTIC_PASSWORD", resticPassword);
        result.put("RESTIC_REPOSITORY", resticRepository);
        if (MapUtils.isNotEmpty(extraEnvMap)) {
            result.putAll(extraEnvMap);
        }
        return result;
    }
}
