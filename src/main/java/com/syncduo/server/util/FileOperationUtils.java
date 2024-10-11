package com.syncduo.server.util;

import com.syncduo.server.exception.SyncDuoException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.util.UUID;

@Slf4j
public class FileOperationUtils {

    public static String getUuid4(
            String rootFolderPath, String relativePath, String fileFullName) throws SyncDuoException {
        if (StringUtils.isAnyBlank(rootFolderPath, fileFullName, relativePath)) {
            throw new SyncDuoException("生成 uuid4 失败, rootFolderPath 或 fileFullName 或 relativePath 为空");
        }
        return UUID.fromString(rootFolderPath + relativePath + fileFullName).toString();
    }

    public static Pair<Timestamp, Timestamp> getFileCrTimeAndMTime(Path file) throws SyncDuoException {
        log.info("正在读取文件:%s 的元数据".formatted(file.toAbsolutePath()));

        if (!Files.exists(file) && Files.isRegularFile(file)) {
            throw new SyncDuoException("文件: %s 不存在".formatted(file.toAbsolutePath()));
        }

        BasicFileAttributes basicFileAttributes;
        try {
            basicFileAttributes = Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new SyncDuoException("无法读取文件:%s 的元数据", e);
        }
        long createTimeStamp = basicFileAttributes.creationTime().toMillis();
        long lastModifiedTimeStamp = basicFileAttributes.lastModifiedTime().toMillis();

        return new ImmutablePair<>(new Timestamp(createTimeStamp), new Timestamp(lastModifiedTimeStamp));
    }

    public static String getFileParentFolderRelativePath(String rootFolderPath, Path file) throws SyncDuoException {
        if (StringUtils.isEmpty(rootFolderPath)) {
            throw new SyncDuoException("无法计算文件的相对路径, rootFolderPath 为空");
        }

        Path rootFolder = Paths.get(rootFolderPath);
        return rootFolder.relativize(file.getParent()).toString();
    }

    public static Pair<String, String> getFileNameAndExtension(Path file) throws SyncDuoException {
        if (ObjectUtils.isEmpty(file)) {
            throw new SyncDuoException("无法获取文件名, file 为空");
        }

        // 获取文件名和文件格式
        String fileName = file.getFileName().toString();
        String fileExtension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            fileExtension = fileName.substring(dotIndex + 1);
            fileName = fileName.substring(0, dotIndex); // Update fileName to exclude the extension
        }
        // 如果文件没有文件格式, 则 fileExtension 为 ""
        return new ImmutablePair<>(fileName, fileExtension);
    }

    public static String getMD5Checksum(Path file) throws SyncDuoException {
        log.info("正在读取文件:%s 的 MD5 checkum".formatted(file.toAbsolutePath()));

        if (!Files.exists(file) && Files.isRegularFile(file)) {
            throw new SyncDuoException("文件: %s 不存在".formatted(file.toAbsolutePath()));
        }

        try (InputStream is = Files.newInputStream(file)) {
            return DigestUtils.md5Hex(is);
        } catch (IOException e) {
            throw new SyncDuoException("无法读取文件:%s 的 MD5 checksum".formatted(file.toAbsolutePath()));
        }
    }

    public static BasicFileAttributes getFileBasicAttributes(Path file) throws SyncDuoException {
        log.info("正在读取文件:%s 的元数据".formatted(file.toAbsolutePath()));

        if (!Files.exists(file) && Files.isRegularFile(file)) {
            throw new SyncDuoException("文件: %s 不存在".formatted(file.toAbsolutePath()));
        }

        try {
            return Files.readAttributes(file, BasicFileAttributes.class);
        } catch (IOException e) {
            throw new SyncDuoException("无法读取文件:%s 的元数据", e);
        }
    }

    public static void scanFileRecursive(String folderPath, SimpleFileVisitor<Path> fileVisitor)
            throws SyncDuoException {
        log.info("正在遍历文件夹: %s".formatted(folderPath));

        Path folder = isFolderPathValid(folderPath);
        try {
            Files.walkFileTree(folder, fileVisitor);
        } catch (IOException e) {
            throw new SyncDuoException("遍历文件出错", e);
        }
    }


    public static void copyFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件复制操作. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath));

        ImmutablePair<Path, Path> pathPair = isFilePathValid(sourcePath, destPath);
        Path sourceFile = pathPair.getLeft();
        Path destFile = pathPair.getRight();
        try {
            Files.copy(sourceFile, destFile);
        } catch (IOException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s", e);
        }
    }

    public static void hardlinkFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件hardlink操作. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath));

        ImmutablePair<Path, Path> pathPair = isFilePathValid(sourcePath, destPath);
        Path sourceFile = pathPair.getLeft();
        Path destFile = pathPair.getRight();

        if (!sourceFile.getRoot().equals(destFile.getRoot())) {
            throw new SyncDuoException("源文件路径:%s 和目标文件路径:%s 不在一个磁盘上".formatted(sourcePath, destPath));
        }

        try {
            Files.createLink(sourceFile, destFile);
        } catch (IOException e) {
            throw new SyncDuoException("文件hardlink失败. 源文件 %s, 目的文件 %s", e);
        }
    }

    private static ImmutablePair<Path, Path> isFilePathValid(String sourcePath, String destPath)
            throws SyncDuoException {
        if (StringUtils.isAnyBlank(sourcePath, destPath)) {
            throw new SyncDuoException("源文件路径:%s 或目标文件路径:%s 为空.".formatted(sourcePath, destPath));
        }

        Path sourceFile = Paths.get(sourcePath);
        Path destFile = Paths.get(destPath);

        if (!Files.exists(sourceFile)) {
            throw new SyncDuoException("源文件路径:%s 不存在".formatted(sourcePath));
        }

        Path parentFolder = destFile.getParent();
        if (ObjectUtils.isNotEmpty(parentFolder) && !Files.exists(parentFolder)) {
            throw new SyncDuoException("目标路径:%s 不存在".formatted(destPath));
        }

        return new ImmutablePair<>(sourceFile, destFile);
    }

    public static Path isFolderPathValid(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("文件夹路径为空");
        }
        Path folderPath = Paths.get(path);
        if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
            return folderPath;
        } else {
            throw new SyncDuoException("文件夹路径:%s 不存在或不是文件夹".formatted(path));
        }
    }
}
