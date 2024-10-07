package com.syncduo.server.util;

import com.syncduo.server.exception.SyncDuoException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileOperationUtils {

    public static void copyFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件复制操作. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath));

        Path sourceFilePath = isFileValid(sourcePath);
        Path destFilePath = isDestPathValid(destPath);

        try {
            Files.copy(sourceFilePath, destFilePath);
        } catch (IOException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s", e);
        }
    }

    public static void hardlinkFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件hardlink操作. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath));

        Path sourceFilePath = isFileValid(sourcePath);
        Path destFilePath = isDestPathValid(destPath);

        try {
            Files.createLink(sourceFilePath, destFilePath);
        } catch (IOException e) {
            throw new SyncDuoException("文件hardlink失败. 源文件 %s, 目的文件 %s", e);
        }
    }

    private static Path isFileValid(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("路径为空");
        }
        Path filePath = Paths.get(path);
        if (Files.exists(filePath) && Files.isRegularFile(filePath) && Files.isReadable(filePath)) {
            return filePath;
        } else {
            throw new SyncDuoException("文件不存在 或 文件不是常规文件 或 文件不可读 %s".formatted(path));
        }
    }

    private static Path isDestPathValid(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("路径为空");
        }
        Path filePath = Paths.get(path);
        Path fileParentPath = filePath.getParent();
        if (Files.exists(fileParentPath)) {
            return filePath;
        } else {
            throw new SyncDuoException("路径不存在 %s".formatted(path));
        }
    }
}
