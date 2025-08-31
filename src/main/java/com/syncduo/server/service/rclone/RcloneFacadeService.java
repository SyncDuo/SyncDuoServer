package com.syncduo.server.service.rclone;

import com.syncduo.server.enums.SyncFlowStatusEnum;
import com.syncduo.server.exception.SyncDuoException;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@Slf4j
public class RcloneFacadeService {

    @Value("${syncduo.server.rclone.job.status.track.timeout.minute:5}")
    private int TIMEOUT;

    @Value("${syncduo.server.rclone.job.status.track.interval.sec:5}")
    private int INTERVAL;

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

    public void init() throws SyncDuoException {
        // todo:
    }

    public CoreStatsResponse getCoreStats() throws SyncDuoException {
        RcloneResponse<CoreStatsResponse> rcloneResponse = this.rcloneService.getCoreStats();
        if (ObjectUtils.isEmpty(rcloneResponse)) {
            throw new SyncDuoException("getCoreStats failed. rcloneResponse is null");
        }
        if (!rcloneResponse.isSuccess()) {
            throw new SyncDuoException("getCoreStats failed. rcloneResponse failed. " +
                    "Error is %s".formatted(rcloneResponse.getSyncDuoException()));
        }
        return rcloneResponse.getData();
    }

    public boolean isSourceFolderExist(String sourceFolderPath) throws SyncDuoException {
        if (StringUtils.isBlank(sourceFolderPath)) {
            throw new SyncDuoException("isSourceFolderExist failed. sourceFolderPath is empty");
        }
        StatsRequest statsRequest = new StatsRequest(
                "/",
                sourceFolderPath
        );
        RcloneResponse<StatsResponse> rcloneResponse = this.rcloneService.getDirStat(statsRequest);
        if (ObjectUtils.isEmpty(rcloneResponse)) {
            throw new SyncDuoException("isSourceFolderExist failed. http response is null");
        }
        if (!rcloneResponse.isSuccess()) {
            throw new SyncDuoException("isSourceFolderExist failed.", rcloneResponse.getSyncDuoException());
        }
        StatsResponse statsResponse = rcloneResponse.getData();
        if (ObjectUtils.isEmpty(statsResponse.getItem())) {
            return false;
        }
        return statsResponse.getItem().isDir();
    }

    public boolean oneWayCheck(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        try {
            EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity);
        } catch (SyncDuoException e) {
            throw new SyncDuoException("oneWayCheck failed.", e);
        }
        CheckRequest checkRequest = new CheckRequest(
                syncFlowEntity.getSourceFolderPath(),
                syncFlowEntity.getDestFolderPath()
        );
        RcloneResponse<CheckResponse> rcloneResponse = this.rcloneService.oneWayCheck(checkRequest);
        if (ObjectUtils.isEmpty(rcloneResponse)) {
            throw new SyncDuoException("oneWayCheck failed. http response is null");
        }
        if (rcloneResponse.isSuccess()) {
            return rcloneResponse.getData().isSuccess();
        } else {
            throw new SyncDuoException("oneWayCheck failed.", rcloneResponse.getSyncDuoException());
        }
    }

    public void copyFile(FilesystemEvent filesystemEvent) throws SyncDuoException {
        String sourceFolderPath = filesystemEvent.getFolder().toAbsolutePath().toString();
        String filePath = filesystemEvent.getFile().toAbsolutePath().toString();
        String fileRelativePath = FilesystemUtil.splitPath(sourceFolderPath, filePath);
        // 根据 filesystem event 的 folder 查询下游 syncflow entity, 过滤 PAUSE 的记录
        List<SyncFlowEntity> downstreamSyncFlowEntityList =
                this.syncFlowService.getBySourceFolderPath(sourceFolderPath, true);
        if (CollectionUtils.isEmpty(downstreamSyncFlowEntityList)) {
            return;
        }
        log.debug("get downstream syncflow as {}", downstreamSyncFlowEntityList);
        for (SyncFlowEntity syncFlowEntity : downstreamSyncFlowEntityList) {
            // syncflow status 修改为 RUNNING
            this.syncFlowService.updateSyncFlowStatus(
                    syncFlowEntity,
                    SyncFlowStatusEnum.RUNNING
            );
            // 数据库插入 copy job
            CopyJobEntity copyJobEntity = this.copyJobService.addCopyJob(syncFlowEntity.getSyncFlowId());
            // 获取 filter criteria as list
            List<String> filterCriteriaAsList = this.syncFlowService.getFilterCriteriaAsList(syncFlowEntity);
            // 创建 copyFileRequest
            CopyFileRequest copyFileRequest = new CopyFileRequest(
                    sourceFolderPath,
                    fileRelativePath,
                    syncFlowEntity.getDestFolderPath(),
                    fileRelativePath,
                    filterCriteriaAsList
            );
            // 发起请求
            RcloneResponse<RcloneAsyncResponse> rcloneResponse = this.rcloneService.copyFile(copyFileRequest);
            log.debug("copy file {}", copyFileRequest);
            // 处理异步请求
            this.handleAsyncRcloneJob(copyJobEntity, rcloneResponse);
        }
    }

    public void syncCopy(SyncFlowEntity syncFlowEntity) throws SyncDuoException {
        try {
            EntityValidationUtil.isSyncFlowEntityValid(syncFlowEntity);
        } catch (SyncDuoException e) {
            throw new SyncDuoException("syncCopy failed. ", e);
        }
        // 数据库插入 copy job
        CopyJobEntity copyJobEntity = this.copyJobService.addCopyJob(syncFlowEntity.getSyncFlowId());
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
        RcloneResponse<RcloneAsyncResponse> rcloneResponse = this.rcloneService.syncCopy(syncCopyRequest);
        log.debug("sync copy {}", syncCopyRequest);
        // 处理异步 response
        this.handleAsyncRcloneJob(copyJobEntity, rcloneResponse);
    }

    private void handleAsyncRcloneJob(
            CopyJobEntity copyJobEntity,
            RcloneResponse<RcloneAsyncResponse> rcloneResponse) {
        try {
            // 请求失败
            if (ObjectUtils.isEmpty(rcloneResponse)) {
                this.copyJobService.markCopyJobAsFailed(
                        copyJobEntity.getCopyJobId(),
                        "handleAsyncRcloneJob failed. rcloneResponse is empty"
                );
                return;
            }
            // 请求失败
            if (!rcloneResponse.isSuccess()) {
                this.copyJobService.markCopyJobAsFailed(
                        copyJobEntity.getCopyJobId(),
                        "handleAsyncRcloneJob failed. rcloneResponse is not success. " +
                                "Error is %s".formatted(rcloneResponse.getSyncDuoException())
                );
                return;
            }
            // 请求成功
            copyJobEntity = this.copyJobService.updateCopyJobRcloneJobId(
                    copyJobEntity.getCopyJobId(),
                    rcloneResponse.getData().getJobId()
            );
            if (ObjectUtils.isEmpty(copyJobEntity)) {
                throw new SyncDuoException("handleAsyncRcloneJob failed. copyJobEntity is empty");
            }
            // track status
            new RcloneJobTracker(
                    moduleDebounceService,
                    copyJobEntity,
                    rcloneService,
                    copyJobService,
                    syncFlowService,
                    INTERVAL,
                    Duration.ofMinutes(TIMEOUT)).start();
        } catch (SyncDuoException e) {
            log.error("handleAsyncRcloneJob failed.", e);
        }
    }

    // Inner class for polling a single job
    static class RcloneJobTracker {

        private final DebounceService.ModuleDebounceService moduleDebounceService;

        private final CopyJobEntity copyJobEntity;

        private JobStatusResponse jobStatusResponse;

        private final RcloneService rcloneService;

        private final CopyJobService copyJobService;

        private final SyncFlowService syncFlowService;

        private final int interval;

        private final Duration timeout;

        private final Instant startTime;

        public RcloneJobTracker(
                DebounceService.ModuleDebounceService moduleDebounceService,
                CopyJobEntity copyJobEntity,
                RcloneService rcloneService,
                CopyJobService copyJobService,
                SyncFlowService syncFlowService,
                int interval,
                Duration timeout) {
            this.moduleDebounceService = moduleDebounceService;
            this.copyJobEntity = copyJobEntity;
            this.rcloneService = rcloneService;
            this.copyJobService = copyJobService;
            this.syncFlowService = syncFlowService;
            this.interval = interval;
            this.timeout = timeout;
            this.startTime = Instant.now();
        }

        public void start() {
            scheduleTask(this::pollJobStatus);
        }

        private void scheduleTask(Runnable task) {
            this.moduleDebounceService.schedule(task, interval);
        }

        private void pollJobStatus() {
            Long copyJobId = copyJobEntity.getCopyJobId();
            Long rcloneJobId = copyJobEntity.getRcloneJobId();
            try {
                // Check timeout
                if (Duration.between(startTime, Instant.now()).compareTo(timeout) > 0) {
                    this.copyJobService.markCopyJobAsFailed(
                            copyJobId,
                            "pollJobStatus failed. " +
                                    "Job %s polling timed out after %s minutes".formatted(rcloneJobId, timeout));
                    return;
                }
                RcloneResponse<JobStatusResponse> rcloneResponse =
                        rcloneService.getJobStatus(new JobStatusRequest(Math.toIntExact(rcloneJobId)));
                if (ObjectUtils.isEmpty(rcloneResponse)) {
                    // 获取请求失败
                    this.copyJobService.markCopyJobAsFailed(
                            copyJobId,
                            "pollJobStatus failed. rcloneResponse is empty."
                    );
                    return;
                }
                if (!rcloneResponse.isSuccess()) {
                    this.copyJobService.markCopyJobAsFailed(
                            copyJobId,
                            "pollJobStatus failed. rcloneResponse is not success. " +
                                    "Error is %s".formatted(rcloneResponse.getSyncDuoException())
                    );
                    return;
                }
                JobStatusResponse jobStatusResponse = rcloneResponse.getData();
                // rclone job 没有完成, 则继续 polling
                if (!jobStatusResponse.isFinished()) {
                    this.scheduleTask(this::pollJobStatus);
                    return;
                }
                if (jobStatusResponse.isSuccess()) {
                    // rclone job 完成且成功, 请求 core stats, 更新数据库
                    log.debug("get job status success {}", jobStatusResponse);
                    this.jobStatusResponse = jobStatusResponse;
                    this.scheduleTask(this::getCoreStatsAndUpdate);
                } else {
                    // rclone job 完成但失败, 刷新数据库, 更新状态和错误信息
                    this.copyJobService.markCopyJobAsFailed(
                            copyJobId,
                            "pollJobStatus failed. rcloneResponse is not success." +
                                    "Error is %s.".formatted(jobStatusResponse.getError())
                    );
                }
            } catch (SyncDuoException e) {
                log.error("pollJobStatus failed.", e);
            }
        }

        private void getCoreStatsAndUpdate() {
            Long copyJobId = copyJobEntity.getCopyJobId();
            Long rcloneJobId = copyJobEntity.getRcloneJobId();
            try {
                CoreStatsRequest coreStatsRequest = new CoreStatsRequest(rcloneJobId);
                log.debug("coreStatsRequest {}", coreStatsRequest);
                RcloneResponse<CoreStatsResponse> rcloneResponse = rcloneService.getCoreStats(coreStatsRequest);
                if (ObjectUtils.isEmpty(rcloneResponse)) {
                    this.copyJobService.markCopyJobAsSuccess(copyJobId, jobStatusResponse, null);
                } else if (!rcloneResponse.isSuccess()) {
                    this.copyJobService.markCopyJobAsSuccess(copyJobId, jobStatusResponse, null);
                } else {
                    this.copyJobService.markCopyJobAsSuccess(copyJobId, jobStatusResponse, rcloneResponse.getData());
                }
                this.moduleDebounceService.debounce(
                        copyJobId.toString(),
                        () -> this.syncFlowService.updateSyncFlowStatus(
                                copyJobEntity.getSyncFlowId(),
                                SyncFlowStatusEnum.SYNC
                        ),
                        interval
                );
            } catch (SyncDuoException e) {
                log.error("getCoreStatsAndUpdate failed.", e);
            }
        }
    }
}
