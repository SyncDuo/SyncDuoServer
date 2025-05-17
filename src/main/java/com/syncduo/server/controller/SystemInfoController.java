package com.syncduo.server.controller;

import com.syncduo.server.bus.FileOperationMonitor;
import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.http.FolderStats;
import com.syncduo.server.model.http.systeminfo.SystemInfoResponse;
import com.syncduo.server.service.bussiness.impl.FolderService;
import com.syncduo.server.service.cache.SyncFlowServiceCache;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.SystemInfoUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;


@RestController
@RequestMapping("/system-info")
@Slf4j
@CrossOrigin
public class SystemInfoController {

    private final SyncFlowServiceCache syncFlowServiceCache;

    private final FolderWatcher folderWatcher;

    private final FolderService folderService;

    private final FileOperationMonitor fileOperationMonitor;

    @Autowired
    public SystemInfoController(
            SyncFlowServiceCache syncFlowServiceCache,
            FolderWatcher folderWatcher,
            FolderService folderService,
            FileOperationMonitor fileOperationMonitor) {
        this.syncFlowServiceCache = syncFlowServiceCache;
        this.folderWatcher = folderWatcher;
        this.folderService = folderService;
        this.fileOperationMonitor = fileOperationMonitor;
    }

    @GetMapping("/get-system-info")
    public SystemInfoResponse getSystemInfo() {
        SystemInfoResponse systemInfoResponse = new SystemInfoResponse();
        // hostname
        try {
            String hostName = SystemInfoUtil.getHostName();
            systemInfoResponse.setHostName(hostName);
        } catch (SyncDuoException e) {
            return systemInfoResponse.onFailed("获取 hostName 失败. 异常是 %s" + e);
        }
        // syncFlowNumber
        List<SyncFlowEntity> allSyncFlow = this.syncFlowServiceCache.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            systemInfoResponse.setSyncFlowNumber(0);
        } else {
            systemInfoResponse.setSyncFlowNumber(allSyncFlow.size());
        }
        // fileCopyRate
        double fileCopyRate = this.fileOperationMonitor.getFileCopyRate();
        systemInfoResponse.setFileCopyRate(fileCopyRate);
        // folderStats
        List<Long> folderInfos = new ArrayList<>(Arrays.asList(0L, 0L, 0L));
        // 获取所有正在使用的 folder
        if (CollectionUtils.isNotEmpty(allSyncFlow)) {
            Set<Long> folderIdSet = new HashSet<>();
            for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                folderIdSet.add(syncFlowEntity.getSourceFolderId());
                folderIdSet.add(syncFlowEntity.getDestFolderId());
            }
            List<FolderEntity> allFolderEntity = this.folderService.listByIds(folderIdSet);
            for (FolderEntity folderEntity : allFolderEntity) {
                try {
                    List<Long> oneFolderInfo = FilesystemUtil.getFolderInfo(folderEntity.getFolderFullPath());
                    folderInfos.set(0, folderInfos.get(0) + oneFolderInfo.get(0));
                    folderInfos.set(1, folderInfos.get(1) + oneFolderInfo.get(1));
                    folderInfos.set(2, folderInfos.get(2) + oneFolderInfo.get(2));
                } catch (SyncDuoException e) {
                    return systemInfoResponse.onFailed("获取 folderStats 失败. 异常是 %s" + e);
                }
            }
        }
        systemInfoResponse.setFolderStats(new FolderStats(folderInfos.get(0), folderInfos.get(1), folderInfos.get(2)));
        // uptime
        systemInfoResponse.setUptime();
        // watchers
        systemInfoResponse.setWatchers(this.folderWatcher.getWatcherNumber());

        return systemInfoResponse.onSuccess("获取 systemInfo 成功");
    }
}
