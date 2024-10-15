package com.syncduo.server.model.lock;

import com.syncduo.server.exception.SyncDuoException;
import lombok.Getter;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FolderConcurrencyState {

    private Set<Long> fileLookUp = ConcurrentHashMap.newKeySet(100);

    @Getter
    private volatile boolean fullScan;

    public boolean isFileLock(Long fileId) throws SyncDuoException {
        if (ObjectUtils.isEmpty(fileId)) {
            throw new SyncDuoException("获取文件锁状态失败, fileId 为空");
        }
        return this.fileLookUp.contains(fileId);
    }

    public void setLock(Long fileId) throws SyncDuoException {

    }
}
