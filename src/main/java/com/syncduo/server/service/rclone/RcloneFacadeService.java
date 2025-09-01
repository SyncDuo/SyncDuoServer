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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@Service
@Slf4j
public class RcloneFacadeService {

    @Value("${syncduo.server.rclone.job.status.track.timeout.sec:5}")
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
            this.createAndStartRcloneJob(syncFlowEntity, () -> rcloneService.copyFile(copyFileRequest));
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
        this.createAndStartRcloneJob(syncFlowEntity, () -> this.rcloneService.syncCopy(syncCopyRequest));
    }

    @Async("generalTaskScheduler")
    protected void createAndStartRcloneJob(
            SyncFlowEntity syncFlowEntity,
            Supplier<RcloneResponse<RcloneAsyncResponse>> supplier) throws SyncDuoException {
        // 创建 copy job
        CopyJobEntity copyJobEntity = this.copyJobService.addCopyJob(syncFlowEntity.getSyncFlowId());
        // 发起请求
        RcloneResponse<RcloneAsyncResponse> rcloneResponse = supplier.get();
        // 失败则记录数据, 并终止逻辑
        Long copyJobId = copyJobEntity.getCopyJobId();
        if (!rcloneResponse.isSuccess()) {
            SyncDuoException syncDuoException = rcloneResponse.getSyncDuoException();
            this.copyJobService.markCopyJobAsFailed(copyJobId, syncDuoException.toString());
            throw new SyncDuoException("createAndStartRcloneJob failed.", syncDuoException);
        }
        // 成功则启动 rclone job status 跟踪
        int rcloneJobId = rcloneResponse.getData().getJobId();
        String trackJobStatusKey = copyJobId.toString();
        this.moduleDebounceService.cancelAfter(
                trackJobStatusKey,
                () -> trackJobStatus(copyJobId, rcloneJobId, trackJobStatusKey, syncFlowEntity),
                INTERVAL,
                TIMEOUT
        );
    }

    private void trackJobStatus(long copyJobId, int rcloneJobId, String jobKey, SyncFlowEntity syncFlowEntity) {
        // 发起请求
        RcloneResponse<JobStatusResponse> jobStatusResponse =
                rcloneService.getJobStatus(new JobStatusRequest(rcloneJobId));
        // rclone 访问失败或者 job status 不是 finish, 则重试
        if (!jobStatusResponse.isSuccess() || !jobStatusResponse.getData().isFinished()) {
            return;
        }
        // 更新数据库
        JobStatusResponse jobStatus = jobStatusResponse.getData();
        if (!jobStatus.isSuccess()) {
            // rclone job 完成但失败, 刷新数据库, 更新状态和错误信息
            this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.FAIL);
            this.copyJobService.markCopyJobAsFailed(copyJobId, jobStatus.getError());
            return;
        }
        // 记录成功
        this.copyJobService.markCopyJobAsSuccess(copyJobId, jobStatus, null);
        this.syncFlowService.updateSyncFlowStatus(syncFlowEntity, SyncFlowStatusEnum.SYNC);
        // 创建定时任务获取 core stats. core stats 更新较慢, 重复获取直到超时
        String trackCoreStatsKey = String.valueOf(rcloneJobId);
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
                    this.moduleDebounceService.cancel(trackCoreStatsKey);
                },
                INTERVAL,
                TIMEOUT
        );
        // job status 获取成功, 取消定时任务
        this.moduleDebounceService.cancel(jobKey);
    }
}
