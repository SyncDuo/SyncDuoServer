package com.syncduo.server.controller;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.api.global.FolderStats;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import com.syncduo.server.model.api.systeminfo.SystemInfo;
import com.syncduo.server.model.api.systeminfo.SystemSettings;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.rclone.core.stat.CoreStatsResponse;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.util.FilesystemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

@RestController
@RequestMapping("/system-info")
@Slf4j
@CrossOrigin
public class SystemInfoController {

    private final FolderWatcher folderWatcher;

    private final SyncFlowService syncFlowService;

    private final RcloneFacadeService rcloneFacadeService;

    private final SystemSettings systemSettings;

    @Autowired
    public SystemInfoController(
            SyncFlowService syncFlowService,
            FolderWatcher folderWatcher,
            RcloneFacadeService rcloneFacadeService,
            SystemSettings systemSettings) {
        this.syncFlowService = syncFlowService;
        this.folderWatcher = folderWatcher;
        this.rcloneFacadeService = rcloneFacadeService;
        this.systemSettings = systemSettings;
    }

    @GetMapping("/get-system-settings")
    public SyncDuoHttpResponse<SystemSettings> getSystemSettings() {
        return SyncDuoHttpResponse.success(systemSettings);
    }

    @GetMapping("/get-system-info")
    public SyncDuoHttpResponse<SystemInfo> getSystemInfo() {
        SystemInfo systemInfo = new SystemInfo();
        // uptime
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        if (ObjectUtils.isEmpty(runtimeMXBean)) {
            throw new ValidationException("spring boot runtimeMXBean is null");
        }
        long uptimeMillis = runtimeMXBean.getUptime();
        systemInfo.setUptime(uptimeMillis);
        // fileCopyRate
        CoreStatsResponse coreStatsResponse = this.rcloneFacadeService.getCoreStats();
        double totalMB = coreStatsResponse.getTotalBytes() / 1024.0 / 1024.0;
        double transferTime = coreStatsResponse.getTransferTime();
        systemInfo.setFileCopyRate(totalMB / transferTime);
        // watchers
        systemInfo.setWatchers(this.folderWatcher.getWatcherNumber());
        // syncFlowNumber and folder stats
        List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
        if (CollectionUtils.isEmpty(allSyncFlow)) {
            systemInfo.setSyncFlowNumber(0);
            return SyncDuoHttpResponse.success(systemInfo);
        }
        systemInfo.setSyncFlowNumber(allSyncFlow.size());
        // 遍历 sync flow 累加 folder stats
        long[] folderStatsArray = {0, 0, 0};
        for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
            List<Long> folderInfo = FilesystemUtil.getFolderInfo(syncFlowEntity.getDestFolderPath());
            folderStatsArray[0] += folderInfo.get(0);
            folderStatsArray[1] += folderInfo.get(1);
            folderStatsArray[2] += folderInfo.get(2);
        }
        FolderStats folderStats = new FolderStats(folderStatsArray);
        systemInfo.setFolderStats(folderStats);
        return SyncDuoHttpResponse.success(systemInfo);
    }
}
