package com.syncduo.server.service.rclone;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.entity.CopyJobEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.internal.FilesystemEvent;
import com.syncduo.server.model.rclone.core.stat.CoreStatsRequest;
import com.syncduo.server.model.rclone.core.stat.CoreStatsResponse;
import com.syncduo.server.model.rclone.global.RcloneAsyncResponse;
import com.syncduo.server.model.rclone.global.RcloneResponse;
import com.syncduo.server.model.rclone.job.status.JobStatusRequest;
import com.syncduo.server.model.rclone.job.status.JobStatusResponse;
import com.syncduo.server.model.rclone.operations.check.CheckRequest;
import com.syncduo.server.model.rclone.operations.check.CheckResponse;
import com.syncduo.server.model.rclone.operations.copyfile.CopyFileRequest;
import com.syncduo.server.model.rclone.operations.stats.StatsRequest;
import com.syncduo.server.model.rclone.operations.stats.StatsResponse;
import com.syncduo.server.model.rclone.sync.copy.SyncCopyRequest;
import com.syncduo.server.service.bussiness.DebounceService;
import com.syncduo.server.service.db.impl.CopyJobService;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.util.EntityValidationUtil;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

@Service
@Slf4j
public class RcloneFacadeService implements DisposableBean {

    @Value("${syncduo.server.rclone.httpBaseUrl}")
    private String httpUrl;

    @Value("${syncduo.server.rclone.httpUser}")
    private String httpUser;

    @Value("${syncduo.server.rclone.httpPassword}")
    private String httpPassword;

    @Value("${syncduo.server.rclone.logFolderPath}")
    private String logFolderPath;

    @Value("${syncduo.server.rclone.jobStatusTrackTimeoutSec}")
    private int TIMEOUT;

    @Value("${syncduo.server.rclone.jobStatusTrackIntervalSec}")
    private int INTERVAL;

    private ExecuteWatchdog watchdog;

    private volatile boolean stopRclone = false;

    private final DebounceService.ModuleDebounceService moduleDebounceService;

    private final CopyJobService copyJobService;

    private final RcloneService rcloneService;

    private final SyncFlowService syncFlowService;

    @Autowired
    public RcloneFacadeService(
            DebounceService debounceService,
            CopyJobService copyJobService,
            RcloneService rcloneService,
            SyncFlowService syncFlowService) {
        this.moduleDebounceService = debounceService.forModule(RcloneFacadeService.class.getSimpleName());
        this.copyJobService = copyJobService;
        this.rcloneService = rcloneService;
        this.syncFlowService = syncFlowService;
    }

    public void init() {
        try {
            this.startRclone();
            this.getCoreStats();
        } catch (Exception e) {
            throw new BusinessException("rclone init failed.", e);
        }
        log.info("rclone init success");
    }

    public CoreStatsResponse getCoreStats() {
        RcloneResponse<CoreStatsResponse> rcloneResponse = this.rcloneService.getCoreStats();
        if (!rcloneResponse.isSuccess()) {
            throw new BusinessException("getCoreStats failed.", rcloneResponse.getBusinessException());
        }
        return rcloneResponse.getData();
    }

    public boolean isSourceFolderExist(String sourceFolderPath) {
        if (StringUtils.isBlank(sourceFolderPath)) {
            throw new ValidationException("isSourceFolderExist failed. sourceFolderPath is empty");
        }
        StatsRequest statsRequest = new StatsRequest(
                "/",
                sourceFolderPath
        );
        RcloneResponse<StatsResponse> rcloneResponse = this.rcloneService.getDirStat(statsRequest);
        if (!rcloneResponse.isSuccess()) {
            throw new BusinessException("isSourceFolderExist failed.", rcloneResponse.getBusinessException());
        }
        StatsResponse statsResponse = rcloneResponse.getData();
        if (ObjectUtils.isEmpty(statsResponse.getItem())) {
            return false;
        }
        return statsResponse.getItem().isDir();
    }

    public boolean oneWayCheck(SyncFlowEntity syncFlowEntity) {
        EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity);
        CheckRequest checkRequest = new CheckRequest(
                syncFlowEntity.getSourceFolderPath(),
                syncFlowEntity.getDestFolderPath()
        );
        // 如果 syncflow 有过滤, 则 oneway check 要加上过滤
        if (StringUtils.isNotBlank(syncFlowEntity.getFilterCriteria())) {
            List<String> filterCriteria = this.syncFlowService.getFilterCriteriaAsList(syncFlowEntity);
            checkRequest.exclude(filterCriteria);
        }
        // 获取 RcloneAsyncResponse
        RcloneResponse<RcloneAsyncResponse> rcloneAsyncResponse = this.rcloneService.oneWayCheck(checkRequest);
        if (!rcloneAsyncResponse.isSuccess()) {
            throw new BusinessException("oneWayCheck failed. rcloneAsyncResponse failed. " +
                    "syncFlowEntity is %s".formatted(syncFlowEntity),
                    rcloneAsyncResponse.getBusinessException());
        }
        // 创建定时任务, 当任务成功时 set future. 超时则 set exception future
        int rcloneJobId = rcloneAsyncResponse.getData().getJobId();
        String trackJobStatusKey = "RcloneJobId::%s".formatted(rcloneJobId);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.moduleDebounceService.cancelAfter(
                trackJobStatusKey,
                () -> this.trackOneWayCheckJob(
                        trackJobStatusKey,
                        rcloneJobId,
                        future
                ),
                INTERVAL,
                TIMEOUT,
                () -> future.obtrudeException(new BusinessException(
                        "oneWayCheck timeout. " +
                        "syncFlowEntity is %s".formatted(syncFlowEntity))
                )
        );
        try {
            return future.get();
        } catch (Exception e) {
            throw new BusinessException("oneWayCheck throw exception.", e);
        }
    }

    private void trackOneWayCheckJob(
            String trackJobStatusKey,
            int rcloneJobId,
            CompletableFuture<Boolean> future) {
        try {
            RcloneResponse<JobStatusResponse> asyncJobStatus =
                    this.rcloneService.getJobStatus(new JobStatusRequest(rcloneJobId));
            // rclone 访问失败或者 job status 不是 finish, 则重试
            if (!asyncJobStatus.isSuccess() || !asyncJobStatus.getData().isFinished()) {
                return;
            }
            JobStatusResponse asyncJobStatusData = asyncJobStatus.getData();
            if (!asyncJobStatusData.isSuccess()) {
                throw new BusinessException("oneWayCheck failed. " +
                        "error is %s".formatted(asyncJobStatusData.getError()));
            }
            // 反序列化 output 为 CheckResponse
            CheckResponse checkResponse =
                    JsonUtil.deserializeObjectToPojo(asyncJobStatusData.getOutput(), CheckResponse.class);
            future.complete(checkResponse.isSuccess());
        } catch (Exception e) {
            future.obtrudeException(new BusinessException("trackOneWayCheckJob failed.", e));
            // oneWayCheck 失败, 取消定时任务
            this.moduleDebounceService.cancel(trackJobStatusKey);
        }
    }

    public void copyFile(FilesystemEvent filesystemEvent) {
        String sourceFolderPath = filesystemEvent.getFolder().toAbsolutePath().toString();
        Path file = filesystemEvent.getFile();
        String filePath = file.toAbsolutePath().toString();
        String fileRelativePath = FilesystemUtil.splitPath(sourceFolderPath, filePath);
        // 根据 filesystem event 的 folder 查询下游 syncflow entity, 过滤 PAUSE 的记录
        List<SyncFlowEntity> downstreamSyncFlowEntityList =
                this.syncFlowService.getBySourceFolderPath(sourceFolderPath, true);
        if (CollectionUtils.isEmpty(downstreamSyncFlowEntityList)) {
            return;
        }
        log.debug("get downstream syncflow as {}", downstreamSyncFlowEntityList);
        for (SyncFlowEntity syncFlowEntity : downstreamSyncFlowEntityList) {
            // 手动 filter, 因为 rclone 设计 copyfile api 不支持 filter
            if (isFileFiltered(file.getFileName().toString(), syncFlowEntity)) {
                continue;
            }
            // syncflow status 修改为 RUNNING
            this.syncFlowService.updateSyncFlowStatus(
                    syncFlowEntity,
                    SyncFlowStatusEnum.RUNNING
            );
            // 创建 copyFileRequest
            CopyFileRequest copyFileRequest = new CopyFileRequest(
                    sourceFolderPath,
                    fileRelativePath,
                    syncFlowEntity.getDestFolderPath(),
                    fileRelativePath
            );
            // 发起请求
            this.createAndStartRcloneCopyJob(syncFlowEntity, () -> rcloneService.copyFile(copyFileRequest));
        }
    }

    public void syncCopy(SyncFlowEntity syncFlowEntity) {
        EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity);
        // 创建 sync copy request
        SyncCopyRequest syncCopyRequest = new SyncCopyRequest(
                syncFlowEntity.getSourceFolderPath(),
                syncFlowEntity.getDestFolderPath()
        );
        // 如果有过滤条件,则加入 sync copy request
        List<String> filterCriteria = this.syncFlowService.getFilterCriteriaAsList(syncFlowEntity);
        if (CollectionUtils.isNotEmpty(filterCriteria)) {
            syncCopyRequest.exclude(filterCriteria);
        }
        // 发起请求
        this.createAndStartRcloneCopyJob(syncFlowEntity, () -> this.rcloneService.syncCopy(syncCopyRequest));
    }

    @Async("generalTaskScheduler")
    protected void createAndStartRcloneCopyJob(
            SyncFlowEntity syncFlowEntity,
            Supplier<RcloneResponse<RcloneAsyncResponse>> supplier) {
        // 创建 copy job
        CopyJobEntity copyJobEntity = this.copyJobService.addCopyJob(syncFlowEntity.getSyncFlowId());
        // 发起请求
        RcloneResponse<RcloneAsyncResponse> rcloneResponse = supplier.get();
        // 失败则记录数据, 并终止逻辑
        Long copyJobId = copyJobEntity.getCopyJobId();
        if (!rcloneResponse.isSuccess()) {
            BusinessException businessException = rcloneResponse.getBusinessException();
            this.copyJobService.markCopyJobAsFailed(copyJobId, businessException.toString());
            this.updateSyncFlowStatusDebounce(syncFlowEntity);
            throw new BusinessException("createAndStartRcloneJob failed.", businessException);
        }
        // 成功则启动 rclone job status 跟踪
        int rcloneJobId = rcloneResponse.getData().getJobId();
        String trackJobStatusKey = "CopyJobId::%s".formatted(copyJobId.toString());
        this.moduleDebounceService.cancelAfter(
                trackJobStatusKey,
                () -> trackCopyJobStatus(copyJobId, rcloneJobId, trackJobStatusKey, syncFlowEntity),
                INTERVAL,
                TIMEOUT,
                () -> {
                    this.copyJobService.markCopyJobAsFailed(copyJobId, "rclone cancel after timeout");
                    this.updateSyncFlowStatusDebounce(syncFlowEntity);
                }
        );
    }

    private void trackCopyJobStatus(
            long copyJobId,
            int rcloneJobId,
            String jobKey,
            SyncFlowEntity syncFlowEntity) {
        // 发起请求
        RcloneResponse<JobStatusResponse> asyncJobResponse =
                rcloneService.getJobStatus(new JobStatusRequest(rcloneJobId));
        // rclone 访问失败或者 job status 不是 finish, 则重试
        if (!asyncJobResponse.isSuccess() || !asyncJobResponse.getData().isFinished()) {
            return;
        }
        // job 完成, 更新数据库
        JobStatusResponse jobStatus = asyncJobResponse.getData();
        if (!jobStatus.isSuccess()) {
            // rclone job 完成但失败, 刷新数据库, 更新状态和错误信息
            this.copyJobService.markCopyJobAsFailed(copyJobId, jobStatus.getError());
            this.updateSyncFlowStatusDebounce(syncFlowEntity);
            // rclone job 失败, 取消任务
            this.moduleDebounceService.cancel(jobKey);
            return;
        }
        // 记录成功
        this.copyJobService.markCopyJobAsSuccess(copyJobId, jobStatus, null);
        this.updateSyncFlowStatusDebounce(syncFlowEntity);
        // 创建定时任务获取 core stats. core stats 更新较慢, 重复获取直到超时
        String trackCoreStatsKey = "RcloneJobId::%s".formatted(String.valueOf(rcloneJobId));
        this.moduleDebounceService.cancelAfter(
                trackCoreStatsKey,
                () -> {
                    CoreStatsRequest coreStatsRequest = new CoreStatsRequest(rcloneJobId);
                    RcloneResponse<CoreStatsResponse> rcloneResponse =
                            this.rcloneService.getCoreStats(coreStatsRequest);
                    if (!rcloneResponse.isSuccess()) {
                        return;
                    }
                    // core stats 获取成功, 更新 copy job
                    CoreStatsResponse coreStats = rcloneResponse.getData();
                    this.copyJobService.markCopyJobAsSuccess(copyJobId, jobStatus, coreStats);
                    // core stats 获取成功, 取消定时任务
                    this.moduleDebounceService.earlyCancel(trackCoreStatsKey);
                },
                INTERVAL,
                TIMEOUT
        );
        // job status 获取成功, 取消定时任务
        this.moduleDebounceService.earlyCancel(jobKey);
    }

    private void updateSyncFlowStatusDebounce(SyncFlowEntity syncFlowEntity) {
        this.moduleDebounceService.debounce(
                "SyncFlowId::%s".formatted(syncFlowEntity.getSyncFlowId()),
                () -> {
                    boolean isSync = this.oneWayCheck(syncFlowEntity);
                    this.syncFlowService.updateSyncFlowStatus(
                            syncFlowEntity,
                            isSync ? SyncFlowStatusEnum.SYNC : SyncFlowStatusEnum.FAILED
                    );
                },
                INTERVAL
        );
    }

    private boolean isFileFiltered(String fileName, SyncFlowEntity syncFlowEntity) {
        List<String> filterCriteriaList = this.syncFlowService.getFilterCriteriaAsList(syncFlowEntity);
        if (CollectionUtils.isEmpty(filterCriteriaList)) {
            return false;
        }
        if (StringUtils.isEmpty(fileName)) {
            return false;
        }
        for (String filterCriteria : filterCriteriaList) {
            // 手动实现 rclone 的 patten.  https://rclone.org/filtering/
            // 目前仅实现 "*" patten
            String regexString = convertToRegex(filterCriteria);
            if (fileName.matches(regexString)) {
                return true;
            }
        }
        return false;
    }

    private String convertToRegex(String filterCriteria) {
        // 转义正则表达式中的特殊字符，除了 '*'
        StringBuilder regexBuilder = new StringBuilder();
        for (char c : filterCriteria.toCharArray()) {
            if (c == '*') {
                regexBuilder.append("[^/]*");
            } else if ("\\^$.|?*+[]{}()".indexOf(c) != -1) {
                // 如果字符是正则表达式中预留的字符, 则增加 "\\" 表示转义
                regexBuilder.append('\\').append(c);
            } else {
                regexBuilder.append(c);
            }
        }
        return regexBuilder.toString();
    }

    // 启动 rclone 的方法, 要求系统中已经安装并正确配置rclone
    private void startRclone() {
        // 参数校验
        if (StringUtils.isAnyBlank(
                this.httpUrl,
                this.httpUser,
                this.httpPassword,
                this.logFolderPath)) {
            throw new ValidationException("startRclone failed. " +
                    "httpUrl, httpUser, httpPassword, logFileLocation is null");
        }
        FilesystemUtil.isFilePathValid(this.logFolderPath);
        CommandLine commandLine = buildStartRcloneCommandLine();
        // 创建执行器
        DefaultExecutor executor = DefaultExecutor.builder().get();
        // 创建 watchdog
        this.watchdog = ExecuteWatchdog.builder().setTimeout(ExecuteWatchdog.INFINITE_TIMEOUT_DURATION).get();
        executor.setWatchdog(this.watchdog);
        // 捕获输出以检查启动错误
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        // 启动 rcd, 在后台线程中执行命令，避免阻塞
        new Thread(() -> {
            try {
                executor.execute(commandLine);
            } catch (Exception e) {
                if (stopRclone) {
                    log.info("stop rclone");
                } else {
                    // 如果不是正常退出, 则记录日志
                    log.error("startRclone failed.", new BusinessException("startRclone failed.", e));
                }
            }
        }, "Rclone-Process").start();
        try {
            // 等待5秒, 保证 rclone 启动
            Thread.sleep(5 * 1000);
            // 判断 rclone 是否运行
            if (ObjectUtils.isEmpty(this.watchdog) || !this.watchdog.isWatching()) {
                throw new BusinessException("startRclone failed. Rclone Watchdog is not running. " +
                        "stderr is %s".formatted(outputStream.toString(StandardCharsets.UTF_8)));
            }
        } catch (InterruptedException e) {
            throw new BusinessException("startRclone failed. Thread got interrupted", e);
        }
    }

    private CommandLine buildStartRcloneCommandLine() {
        CommandLine commandLine = new CommandLine("rclone");
        commandLine.addArgument("rcd");
        // rclone 认证设置
        commandLine.addArgument("--rc-user=%s".formatted(this.httpUser));
        commandLine.addArgument("--rc-pass=%s".formatted(this.httpPassword));
        // rclone rcd 地址设置
        commandLine.addArgument("--rc-addr");
        commandLine.addArgument(this.httpUrl);
        // rclone web 设置
        commandLine.addArgument("--rc-web-gui");
        commandLine.addArgument("--rc-web-gui-no-open-browser");
        // rclone 日志设置
        commandLine.addArgument("--log-file=%s/rclone.log".formatted(this.logFolderPath));
        commandLine.addArgument("--log-level=INFO");
        return commandLine;
    }

    @Override
    public void destroy() {
        this.stopRclone = true;
        if (ObjectUtils.isNotEmpty(this.watchdog)) {
            this.watchdog.destroyProcess();
        }
    }
}
