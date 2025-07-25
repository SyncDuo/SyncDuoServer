package com.syncduo.server.controller;

import com.syncduo.server.bus.FolderWatcher;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.FolderStats;
import com.syncduo.server.model.entity.SyncFlowEntity;
import com.syncduo.server.model.api.systeminfo.SystemInfoResponse;
import com.syncduo.server.model.rclone.core.stat.CoreStatsResponse;
import com.syncduo.server.service.db.impl.SyncFlowService;
import com.syncduo.server.service.rclone.RcloneFacadeService;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.SystemInfoUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.*;

@RestController
@RequestMapping("/system-info")
@Slf4j
@CrossOrigin
public class SystemInfoController {

    private final FolderWatcher folderWatcher;

    private final SyncFlowService syncFlowService;

    private final RcloneFacadeService rcloneFacadeService;

    @Autowired
    public SystemInfoController(
            SyncFlowService syncFlowService,
            FolderWatcher folderWatcher,
            RcloneFacadeService rcloneFacadeService) {
        this.syncFlowService = syncFlowService;
        this.folderWatcher = folderWatcher;
        this.rcloneFacadeService = rcloneFacadeService;
    }

    @GetMapping("/get-system-info")
    public SystemInfoResponse getSystemInfo() {
        SystemInfoResponse systemInfoResponse = new SystemInfoResponse();
        try {
            // hostname
            String hostName = SystemInfoUtil.getHostName();
            systemInfoResponse.setHostName(hostName);
            // uptime
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            if (ObjectUtils.isEmpty(runtimeMXBean)) {
                throw new SyncDuoException("spring boot runtimeMXBean is null");
            }
            long uptimeMillis = runtimeMXBean.getUptime();
            systemInfoResponse.setUptime(uptimeMillis);
            // fileCopyRate
            CoreStatsResponse coreStatsResponse = this.rcloneFacadeService.getCoreStats();
            double totalMB = coreStatsResponse.getTotalBytes() / 1024.0 / 1024.0;
            double transferTime = coreStatsResponse.getTransferTime();
            systemInfoResponse.setFileCopyRate(totalMB / transferTime);
            // watchers
            systemInfoResponse.setWatchers(this.folderWatcher.getWatcherNumber());
            // syncFlowNumber and folder stats
            List<SyncFlowEntity> allSyncFlow = this.syncFlowService.getAllSyncFlow();
            if (CollectionUtils.isEmpty(allSyncFlow)) {
                systemInfoResponse.setSyncFlowNumber(0);
                return systemInfoResponse.onSuccess("获取 systemInfo 成功");
            }
            systemInfoResponse.setSyncFlowNumber(allSyncFlow.size());
            // 遍历 sync flow 累加 folder stats
            long[] folderStatsArray = {0, 0, 0};
            for (SyncFlowEntity syncFlowEntity : allSyncFlow) {
                List<Long> folderInfo = FilesystemUtil.getFolderInfo(syncFlowEntity.getDestFolderPath());
                folderStatsArray[0] += folderInfo.get(0);
                folderStatsArray[1] += folderInfo.get(1);
                folderStatsArray[2] += folderInfo.get(2);
            }
            FolderStats folderStats = new FolderStats(folderStatsArray);
            systemInfoResponse.setFolderStats(folderStats);
            return systemInfoResponse.onSuccess("获取 systemInfo 成功");
        } catch (SyncDuoException e) {
            return systemInfoResponse.onFailed("getSystemInfo failed. %s".formatted(e.getMessage()));
        }
    }
}
