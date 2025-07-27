package com.syncduo.server.service.restic;

import com.syncduo.server.enums.ResticExitCodeEnum;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.restic.global.ExitErrors;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ResticParser<T1, T2> {

    private final String RESTIC_PASSWORD;

    private final String RESTIC_REPOSITORY;

    private final CommandLine commandLine;

    private Map<String, String> extraEnvironmentVariable = null;

    private ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(Duration.ofSeconds(60)).get();

    public ResticParser(String resticPassword, String resticRepository, CommandLine commandLine) {
        RESTIC_PASSWORD = resticPassword;
        RESTIC_REPOSITORY = resticRepository;
        this.commandLine = commandLine;
    }

    public CompletableFuture<ResticExecResult<T1, T2>> execute(
            String workingDirectory,
            OnProcessSuccess<T1> onSuccessHandler,
            OnProcessFailedAgg<T2> onFailedAggHandler,
            OnProcessFailed<T2> onFailedHandler
    ) throws SyncDuoException {
        Executor executor = DefaultExecutor.builder().get();
        CompletableFuture<ResticExecResult<T1, T2>> future = new CompletableFuture<>();
        if (ObjectUtils.allNotNull(onFailedAggHandler, onFailedHandler)) {
            throw new SyncDuoException("onFailedAggHandler and onFailedHandler are both not null");
        }
        // 0: exitErrorHandler, 1: stdAggregateHandler, 2: onFailedHandler
        int stdErrHandlerFlag;
        if (ObjectUtils.allNull(onFailedAggHandler, onFailedHandler)) {
            stdErrHandlerFlag = 0;
        } else {
            stdErrHandlerFlag = ObjectUtils.isEmpty(onFailedAggHandler) ? 2 : 1;
        }
        // 1. 工作目录
        if (StringUtils.isNotBlank(workingDirectory)) {
            executor.setWorkingDirectory(new File(workingDirectory));
            log.debug("workingDirectory: {}", executor.getWorkingDirectory().getAbsolutePath());
        }
        // 2. 创建输出流处理器（捕获 stdout/stderr）
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr);
        executor.setStreamHandler(streamHandler);
        // 3. 设置超时
        executor.setWatchdog(watchdog);
        // 4. 非阻塞执行
        try {
            executor.execute(this.commandLine, getEnv(), new ExecuteResultHandler() {
                @Override
                public void onProcessComplete(int exitCode) {
                    try {
                        T1 result = onSuccessHandler.apply(getStdAsString(stdout));
                        if (ObjectUtils.isEmpty(result)) {
                            throw new SyncDuoException("onProcessComplete failed. result is null.");
                        }
                        future.complete(ResticExecResult.success(ResticExitCodeEnum.SUCCESS, result));
                    } catch (Exception e) {
                        future.complete(ResticExecResult.failed(e));
                    }
                }

                @Override
                public void onProcessFailed(ExecuteException e) {
                    int exitValue = e.getExitValue();
                    ResticExitCodeEnum resticExitCodeEnum = ResticExitCodeEnum.fromCode(exitValue);
                    if (stdErrHandlerFlag == 0) {
                        future.complete(parseExitError(getStdAsString(stderr)));
                    }
                    try {
                        if (stdErrHandlerFlag == 1) {
                            List<T2> result = onFailedAggHandler.apply(getStdAsString(stderr));
                            future.complete(ResticExecResult.failed(resticExitCodeEnum, result));
                        } else {
                            T2 result = onFailedHandler.apply(getStdAsString(stderr));
                            future.complete(ResticExecResult.failed(resticExitCodeEnum, result));
                        }
                    } catch (Exception ex) {
                        // 再尝试一次使用 exitError 解析
                        future.complete(parseExitError(getStdAsString(stderr)));
                    }
                }
            });
        } catch (IOException e) {
            future.complete(ResticExecResult.failed(e));
        }
        return future;
    }

    private String getStdAsString(ByteArrayOutputStream std) {
        return std.toString(StandardCharsets.UTF_8).trim();
    }

    private Map<String, String> getEnv() {
        // 获取当前系统变量
        Map<String, String> result = new HashMap<>(System.getenv());
        // 设置 RESTIC 密码和备份目录
        result.put("RESTIC_PASSWORD", RESTIC_PASSWORD);
        result.put("RESTIC_REPOSITORY", RESTIC_REPOSITORY);
        if (MapUtils.isNotEmpty(extraEnvironmentVariable)) {
            result.putAll(extraEnvironmentVariable);
        }
        return result;
    }

    private static <T1, T2> ResticExecResult<T1, T2> parseExitError(String stderr) {
        try {
            ExitErrors exitErrors = JsonUtil.parseResticJsonDocument(
                    stderr,
                    ExitErrors.class
            );
            return ResticExecResult.failed(exitErrors);
        } catch (SyncDuoException e) {
            return ResticExecResult.failed(e);
        }
    }

    @FunctionalInterface
    public interface OnProcessSuccess<T> {
        T apply(String stdout) throws Exception;
    }

    @FunctionalInterface
    public interface OnProcessFailedAgg<T> {
        List<T> apply(String stderr) throws Exception;
    }

    @FunctionalInterface
    public interface OnProcessFailed<T> {
        T apply(String stderr) throws Exception;
    }
}
