package com.syncduo.server.service.rslsync;

import com.syncduo.server.enums.RslsyncFolderSyncLevelEnum;
import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.rslsync.folder.FolderInfo;
import com.syncduo.server.model.rslsync.folder.FolderInfoResponse;
import com.syncduo.server.model.rslsync.global.RslsyncResponse;
import com.syncduo.server.model.rslsync.settings.FolderStoragePath;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class RslSyncFacadeService {

    @Value("${syncduo.server.rslsync.folderHostPath}")
    private String rslsyncFolderHostPath;

    private boolean isRslsyncContainerize = true;

    private final RslsyncService rslsyncService;

    private final SyncFlowService syncFlowService;

    public RslSyncFacadeService(
            RslsyncService rslsyncService,
            SyncFlowService syncFlowService) {
        this.rslsyncService = rslsyncService;
        this.syncFlowService = syncFlowService;
    }

    public void init() {
        // http api 测试 rslsync 是否存活
        RslsyncResponse<FolderStoragePath> response = this.rslsyncService.getFolderStoragePath();
        if (!response.isSuccess()) {
            throw new BusinessException("rslsync init failed. rslsync not reachable");
        }
        if (StringUtils.isBlank(rslsyncFolderHostPath)) {
            // host path 没有配置, 则认为 rslsync 运行在宿主机上
            isRslsyncContainerize = false;
        } else {
            // 否则验证 host path 配置的正确性
            FilesystemUtil.isFolderPathValid(rslsyncFolderHostPath);
        }
    }

    public List<String> getPendingSourceFolder() {
        RslsyncResponse<FolderInfoResponse> syncFolderInfoResponse = this.rslsyncService.getSyncFolderInfo();
        if (!syncFolderInfoResponse.isSuccess()) {
            log.warn("getPendingSourceFolder failed. rslsync not reachable",
                    syncFolderInfoResponse.getBusinessException());
            return Collections.emptyList();
        }
        // 过滤只保留已经 sync 的 folder
        List<FolderInfo> sourceFolderList = syncFolderInfoResponse.getData().getFolders();
        List<FolderInfo> syncedSourceFolderList = new ArrayList<>();
        for (FolderInfo folderInfo : sourceFolderList) {
            Integer syncLevel = folderInfo.getSyncLevel();
            if (syncLevel == RslsyncFolderSyncLevelEnum.SYNCED.getSyncLevel()
                    || syncLevel == RslsyncFolderSyncLevelEnum.SELECTIVE_SYNC.getSyncLevel()) {
                syncedSourceFolderList.add(folderInfo);
            }
        }
        if (CollectionUtils.isEmpty(syncedSourceFolderList)) {
            return Collections.emptyList();
        }
        // 获取所有 syncflow 比较
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        List<String> folderHostPathList = this.rslsyncFolderPath2HostPath(syncedSourceFolderList);
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            return folderHostPathList;
        }
        List<String> result = new ArrayList<>();
        for (String folderHostPath : folderHostPathList) {
            result.add(folderHostPath);
            for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                if (syncFlowEntity.getSourceFolderPath().equals(folderHostPath)) {
                    result.remove(folderHostPath);
                }
            }
        }
        return result;
    }

    private List<String> rslsyncFolderPath2HostPath(List<FolderInfo> folderInfoList) {
        if (CollectionUtils.isEmpty(folderInfoList)) {
            return Collections.emptyList();
        }
        if (!isRslsyncContainerize) {
            return folderInfoList.stream().map(FolderInfo::getPath).toList();
        }
        List<String> result = new ArrayList<>();
        // 获取 folder storage path
        RslsyncResponse<FolderStoragePath> response = this.rslsyncService.getFolderStoragePath();
        if (!response.isSuccess()) {
            throw new BusinessException("rslsyncFolderPath2HostPath failed. get folder storage path failed.",
                    response.getBusinessException());
        }
        String rslsyncFolderStoragePath = response.getData().getValue();
        for (FolderInfo folderInfo : folderInfoList) {
            String containerFolderPath = folderInfo.getPath();
            String folderHostPath = containerFolderPath.replace(
                    rslsyncFolderStoragePath,
                    this.rslsyncFolderHostPath
            );
            result.add(folderHostPath);
        }
        return result;
    }
}
