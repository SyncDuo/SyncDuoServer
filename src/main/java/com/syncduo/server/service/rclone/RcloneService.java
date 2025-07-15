package com.syncduo.server.service.rclone;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.rclone.core.stat.CoreStatsRequest;
import com.syncduo.server.model.rclone.core.stat.CoreStatsResponse;
import com.syncduo.server.model.rclone.core.transferred.TransferredStatResponse;
import com.syncduo.server.model.rclone.global.ErrorInfo;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;


@Slf4j
@Service
public class RcloneService {

    private final RestClient restClient;

    @Autowired
    protected RcloneService(RestClient restClient) {
        this.restClient = restClient;
    }

    @Deprecated
    protected RcloneResponse<TransferredStatResponse> getTransferredStat() {
        return this.post(
                "core/transferred",
                TransferredStatResponse.class
        );
    }

    protected RcloneResponse<StatsResponse> getDirStat(
            StatsRequest statsRequest) throws SyncDuoException {
        if (ObjectUtils.isEmpty(statsRequest)) {
            throw new SyncDuoException("getDirStat failed. statsRequest is empty");
        }
        if (StringUtils.isAnyBlank(
                statsRequest.getFs(),
                statsRequest.getRemote()
        )) {
            throw new SyncDuoException("getDirStat failed. " +
                    "fs or remote is blank");
        }
        return this.post(
                "operations/stat",
                statsRequest,
                StatsResponse.class
        );
    }

    // 获得单个Job的stats
    protected RcloneResponse<CoreStatsResponse> getCoreStats(
            CoreStatsRequest coreStatsRequest) throws SyncDuoException {
        if (ObjectUtils.anyNull(coreStatsRequest, coreStatsRequest.getGroup())) {
            throw new SyncDuoException("getCoreStats failed. " +
                    "coreStatsRequest or group is null" +
                    "%s".formatted(coreStatsRequest));
        }
        return this.post(
                "core/stats",
                coreStatsRequest,
                CoreStatsResponse.class
        );
    }

    // 获得全部Job聚合的stat
    protected RcloneResponse<CoreStatsResponse> getCoreStats() throws SyncDuoException {
        CoreStatsRequest coreStatsRequest = new CoreStatsRequest("");
        return this.post(
                "core/stats",
                coreStatsRequest,
                CoreStatsResponse.class
        );
    }

    protected RcloneResponse<JobStatusResponse> getJobStatus(
            JobStatusRequest jobStatusRequest) throws SyncDuoException {
        if (ObjectUtils.anyNull(jobStatusRequest, jobStatusRequest.getJobId())) {
            throw new SyncDuoException("getJobStatus failed. " +
                    "jobStatusRequest or jobId is null." +
                    "%s".formatted(jobStatusRequest));
        }
        return this.post(
                "job/status",
                jobStatusRequest,
                JobStatusResponse.class
        );
    }

    protected RcloneResponse<RcloneAsyncResponse> copyFile(
            CopyFileRequest copyFileRequest) throws SyncDuoException {
        if (ObjectUtils.anyNull(
                copyFileRequest,
                copyFileRequest.getSrcFs(),
                copyFileRequest.getSrcRemote(),
                copyFileRequest.getDstFs(),
                copyFileRequest.getDstRemote())) {
            throw new SyncDuoException("syncCopy failed." +
                    "copyFileRequest, srcFs, srcRemote, dstFs, dstRemote is null." +
                    "%s".formatted(copyFileRequest));
        }
        return this.postAsyncRequest(
                "operations/copyfile",
                copyFileRequest,
                RcloneAsyncResponse.class
        );
    }

    protected RcloneResponse<RcloneAsyncResponse> syncCopy(
            SyncCopyRequest syncCopyRequest) throws SyncDuoException {
        if (ObjectUtils.anyNull(syncCopyRequest, syncCopyRequest.getSrcFs(), syncCopyRequest.getDstFs())) {
            throw new SyncDuoException("syncCopy failed." +
                    "copyRequest, srcFs, dstFs is null." +
                    "%s".formatted(syncCopyRequest));
        }
        return this.postAsyncRequest(
                "sync/copy",
                syncCopyRequest,
                RcloneAsyncResponse.class
        );
    }

    protected RcloneResponse<CheckResponse> oneWayCheck(
            CheckRequest checkRequest) throws SyncDuoException {
        if (ObjectUtils.anyNull(checkRequest, checkRequest.getSrcFs(), checkRequest.getDstFs())) {
            throw new SyncDuoException("oneWayCheck failed." +
                    "checkRequest, srcFs or dstFs is null." +
                    "%s".formatted(checkRequest));
        }
        return this.post(
                "operations/check",
                checkRequest,
                CheckResponse.class
        );
    }

    private <Res> RcloneResponse<Res> post(
            String url, Class<Res> clazz) {
        return this.handleClientResponse(
                this.restClient.post().uri(url),
                clazz
        );
    }

    private <Req, Res> RcloneResponse<Res> post(
            String url, Req request, Class<Res> clazz) {
        return this.handleClientResponse(
                this.restClient.post().uri(url).body(request),
                clazz
        );
    }

    private <Req, Res> RcloneResponse<Res> postAsyncRequest(
            String url, Req request, Class<Res> clazz) {
        return this.handleClientResponse(
                this.restClient.post()
                        .uri(uriBuilder ->
                                uriBuilder.path(url).queryParam("_async", "true").build())
                        .body(request),
                clazz
        );
    }

    private <T> RcloneResponse<T> handleClientResponse(
            RestClient.RequestBodySpec requestBodySpec,
            Class<T> dataType) {
        try {
            return requestBodySpec.exchange((httpRequest, httpResponse) -> {
                HttpStatusCode statusCode = httpResponse.getStatusCode();
                if (statusCode.is2xxSuccessful()) {
                    return RcloneResponse.success(statusCode.value(), httpResponse.bodyTo(dataType));
                } else {
                    // 处理错误http响应（非2xx）
                    return RcloneResponse.error(statusCode.value(), httpResponse.bodyTo(ErrorInfo.class));
                }
            });
        } catch (Exception e) {
            // 处理其他异常
            return RcloneResponse.error(e);
        }
    }
}
