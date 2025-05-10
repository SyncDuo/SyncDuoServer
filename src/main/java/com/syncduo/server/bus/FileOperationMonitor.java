package com.syncduo.server.bus;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.FolderEntity;
import com.syncduo.server.util.FilesystemUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


@Service
@Slf4j
public class FileOperationMonitor {

    @Getter
    private volatile double fileCopyRate = 0; // xx.xx MB/s

    private final AtomicLong byteCopied = new AtomicLong(0);

    private volatile long fileCopyStart = 0;

    private volatile long fileCopyEnd = 0;

    private static final long IDLE_TIMEOUT_MILLIS = 1 * 60 * 1000; // 1 minute without file copy consider "idle"

    // <rootFolderId>:rootFolderPathString
    private final ConcurrentHashMap<Long, String> whitelistMap = new ConcurrentHashMap<>(100);

    public Path hardlinkFile(
            Long sourceFolderId,
            String sourceFileFullPath,
            Long destFolderId,
            String destFileFullPath) throws SyncDuoException {
        // 检验 sourceFolder 和 destFolder
        if (this.isFileAccessValid(sourceFolderId, sourceFileFullPath) &&
                this.isFileAccessValid(destFolderId, destFileFullPath)) {
            return FilesystemUtil.hardlinkFile(sourceFileFullPath, destFileFullPath);
        }
        throw new SyncDuoException("hardlinkFile failed. file operation illegal. " +
                "sourceFolderId is %s, sourceFileFullPath is %s; ".formatted(sourceFolderId, sourceFileFullPath) +
                "destFolderId is %s, destFolderId is %s".formatted(destFolderId, destFileFullPath));
    }

    public void deleteFile(
            Long folderId,
            Path file) throws SyncDuoException {
        // 检验 folder path
        if (this.isFileAccessValid(folderId, file.toAbsolutePath().toString())) {
            FilesystemUtil.deleteFile(file);
        }
        throw new SyncDuoException("copyFile failed. file operation illegal. " +
                "folderId is %s, file is %s; ".formatted(folderId, file));
    }

    public Path copyFile(
            Long sourceFolderId,
            Path sourceFile,
            Long destFolderId,
            String destFileFullPath) throws SyncDuoException {
        // 检验 sourceFolder 和 destFolder
        if (this.isFileAccessValid(sourceFolderId, sourceFile.toAbsolutePath().toString()) &&
                this.isFileAccessValid(destFolderId, destFileFullPath)) {
            // 记录 copy file 开始时间
            this.setFileCopyStart();
            // 执行文件复制
            Path file = FilesystemUtil.copyFile(sourceFile, destFileFullPath);
            // 记录 copy file 结束时间和 bytesCopy
            this.setFileCopyEnd(FilesystemUtil.getFileSizeInBytes(file));
            // 返回结果
            return file;
        }
        throw new SyncDuoException("copyFile failed. file operation illegal. " +
                "sourceFolderId is %s, sourceFileFullPath is %s; ".formatted(sourceFolderId, sourceFile) +
                "destFolderId is %s, destFolderId is %s".formatted(destFolderId, destFileFullPath));
    }

    public Path updateFileByCopy(
            Long sourceFolderId,
            Path sourceFile,
            Long destFolderId,
            Path destFile) throws SyncDuoException {
        // 检验 sourceFolder 和 destFolder
        if (this.isFileAccessValid(sourceFolderId, sourceFile.toAbsolutePath().toString()) &&
                this.isFileAccessValid(destFolderId, destFile.toAbsolutePath().toString())) {
            // 记录 copy file 开始时间
            this.setFileCopyStart();
            // 执行文件复制
            Path file = FilesystemUtil.updateFileByCopy(sourceFile, destFile);
            // 记录 copy file 结束时间和 bytesCopy
            this.setFileCopyEnd(FilesystemUtil.getFileSizeInBytes(file));
            // 返回结果
            return file;
        }
        throw new SyncDuoException("updateFileByCopy failed. file operation illegal. " +
                "sourceFolderId is %s, sourceFile is %s; ".formatted(sourceFolderId, sourceFile) +
                "destFolderId is %s, destFile is %s".formatted(destFolderId, destFile));
    }

    public void addWhitelist(FolderEntity... folderEntityList) {
        for (FolderEntity folderEntity : folderEntityList) {
            this.whitelistMap.put(folderEntity.getFolderId(), folderEntity.getFolderFullPath());
        }
    }

    public void removeWhitelist(Long... rootFolderIdList) {
        for (Long rootFolderId : rootFolderIdList) {
            this.whitelistMap.remove(rootFolderId);
        }
    }

    public boolean isFileEventInValid(Long rootFolderId) {
        return !this.whitelistMap.containsKey(rootFolderId);
    }

    public double getFileCopyRate() {
        long byteCopied = this.byteCopied.get();
        if (byteCopied != 0 && this.fileCopyStart != 0 && this.fileCopyEnd != 0) {
            this.fileCopyRate = (byteCopied * 1000.0) / ((this.fileCopyEnd - this.fileCopyStart) * 1_000_000.0);
        }
        return this.fileCopyRate;
    }

    private void setFileCopyStart() {
        if (this.cleanSession()) {
            this.fileCopyStart = System.currentTimeMillis();
        }
    }

    private void setFileCopyEnd(long fileSizeInBytes) {
        this.fileCopyEnd = System.currentTimeMillis();
        long byteCopied = this.byteCopied.addAndGet(fileSizeInBytes);
        // 刷新 fileCopyRate
        // 兜底判断, 防止 setFileCopyEnd 在 setFileCopyStart 前调用
        if (this.fileCopyStart == 0) {
            log.warn("setFileCopyEnd called before setFileCopyStart");
            return;
        }
        this.fileCopyRate = (byteCopied * 1000.0) / ((this.fileCopyEnd - this.fileCopyStart) * 1_000_000.0);
    }

    private boolean cleanSession() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - this.fileCopyEnd > IDLE_TIMEOUT_MILLIS) {
            log.debug("FileOperationMonitor clean session");
            this.fileCopyStart = 0;
            this.fileCopyEnd = 0;
            this.byteCopied.set(0);
            return true;
        }
        return false;
    }

    private boolean isFileAccessValid(Long rootFolderId, String filePathString) {
        String rootFolderPathString = this.whitelistMap.get(rootFolderId);
        if (StringUtils.isBlank(rootFolderPathString)) {
            return false;
        }
        return filePathString.startsWith(rootFolderPathString);
    }
}
