package com.syncduo.server.mq;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.entity.RootFolderEntity;
import com.syncduo.server.util.FileOperationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class FileAccessValidator {

    // <rootFolderId>:rootFolderPathString
    private final ConcurrentHashMap<Long, String> whitelistMap = new ConcurrentHashMap<>(100);

    public Path hardlinkFile(
            Long sourceFolderId,
            String sourceFileFullPath,
            Long destFolderId,
            String destFileFullPath) throws SyncDuoException {
        // 检验 sourceFolder 和 internalFolder
        if (this.isFileAccessValid(sourceFolderId, sourceFileFullPath) &&
                this.isFileAccessValid(destFolderId, destFileFullPath)) {
            return FileOperationUtils.hardlinkFile(sourceFileFullPath, destFileFullPath);
        }
        throw new SyncDuoException("hardlinkFile failed. file operation illegal. " +
                "sourceFolderId is %s, sourceFileFullPath is %s; ".formatted(sourceFolderId, sourceFileFullPath) +
                "destFolderId is %s, destFolderId is %s".formatted(destFolderId, destFileFullPath));
    }

    public Path copyFile(
            Long sourceFolderId,
            String sourceFileFullPath,
            Long destFolderId,
            String destFileFullPath) throws SyncDuoException {
        // 检验 sourceFolder 和 internalFolder
        if (this.isFileAccessValid(sourceFolderId, sourceFileFullPath) &&
                this.isFileAccessValid(destFolderId, destFileFullPath)) {
            return FileOperationUtils.copyFile(sourceFileFullPath, destFileFullPath);
        }
        throw new SyncDuoException("copyFile failed. file operation illegal. " +
                "sourceFolderId is %s, sourceFileFullPath is %s; ".formatted(sourceFolderId, sourceFileFullPath) +
                "destFolderId is %s, destFolderId is %s".formatted(destFolderId, destFileFullPath));
    }

    public Path updateFileByCopy(
            Long sourceFolderId,
            Path sourceFile,
            Long destFolderId,
            Path destFile) throws SyncDuoException {
        // 检验 sourceFolder 和 internalFolder
        if (this.isFileAccessValid(sourceFolderId, sourceFile.toAbsolutePath().toString()) &&
                this.isFileAccessValid(destFolderId, destFile.toAbsolutePath().toString())) {
            return FileOperationUtils.updateFileByCopy(sourceFile, destFile);
        }
        throw new SyncDuoException("updateFileByCopy failed. file operation illegal. " +
                "sourceFolderId is %s, sourceFile is %s; ".formatted(sourceFolderId, sourceFile) +
                "destFolderId is %s, destFile is %s".formatted(destFolderId, destFile));
    }

    public void addWhitelist(RootFolderEntity... rootFolderEntityList) {
        for (RootFolderEntity rootFolderEntity : rootFolderEntityList) {
            this.whitelistMap.put(rootFolderEntity.getRootFolderId(), rootFolderEntity.getRootFolderFullPath());
        }
    }

    public boolean isFileEventValid(Long rootFolderId) {
        return this.whitelistMap.containsKey(rootFolderId);
    }

    public void removeWhitelist(Long... rootFolderIdList) {
        for (Long rootFolderId : rootFolderIdList) {
            this.whitelistMap.remove(rootFolderId);
        }
    }

    private boolean isFileAccessValid(Long rootFolderId, String filePathString) {
        String rootFolderPathString = this.whitelistMap.get(rootFolderId);
        if (StringUtils.isBlank(rootFolderPathString)) {
            return false;
        }
        return filePathString.startsWith(rootFolderPathString);
    }
}
