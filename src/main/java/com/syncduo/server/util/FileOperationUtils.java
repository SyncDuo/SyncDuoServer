package com.syncduo.server.util;

import com.syncduo.server.exception.SyncDuoException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

@Slf4j
@Component
public class FileOperationUtils {

    @Value("${syncduo.server.filelock.retry.count:3}")
    private static int fileLockRetryCount = 3;

    @Value("${syncduo.server.filelock.retry.interval:3000L}")
    private static long fileLockWaitInterval = 5000L;

    public static Path concateStringToPath(
            String folderPath,
            String relativePath,
            String fileName,
            String fileExtension) throws SyncDuoException {
        if (StringUtils.isAnyBlank(folderPath, relativePath, fileName, fileExtension)) {
            throw new SyncDuoException("folderPath, relativePath, fileName, fileExtension 存在空值");
        }
        String filePath = folderPath + relativePath + fileName + fileExtension;
        return isFilePathValid(filePath);
    }

    public static String concatePathString(
            String folderPath,
            String relativePath,
            String fileName,
            String fileExtension) throws SyncDuoException {
        if (StringUtils.isAnyBlank(folderPath, relativePath, fileName, fileExtension)) {
            throw new SyncDuoException("folderPath, relativePath, fileName, fileExtension 存在空值");
        }
        return folderPath + relativePath + fileName + fileExtension;
    }

    public static Path createContentFolder(
            String sourceFolderFullPath,
            String contentFolderFullPath) throws SyncDuoException {
        Path sourceFolder = isFolderPathValid(sourceFolderFullPath);
        Path contentFolderParent = isFolderPathValid(contentFolderFullPath);
        Path contentFolder = contentFolderParent.resolve(sourceFolder.getFileName());
        if (Files.exists(contentFolder)) {
            return contentFolder;
        }
        return createFolder(contentFolder);
    }

    public static String getContentFolderFullPath(
            String sourceFolderFullPath,
            String contentFolderFullPath) throws SyncDuoException {
        Path sourceFolder = isFolderPathValid(sourceFolderFullPath);
        Path contentFolderParent = isFolderPathValid(contentFolderFullPath);
        Path contentFolder = contentFolderParent.resolve(sourceFolder.getFileName());

        return contentFolder.toAbsolutePath().toString();
    }

    public static String getInternalFolderFullPath(String sourceFolderFullPath) throws SyncDuoException {
        Path sourceFolder = isFolderPathValid(sourceFolderFullPath);
        Path sourceFolderParent = sourceFolder.getParent();
        String internalFolderName = "." + sourceFolder.getFileName() + "-internal";

        return sourceFolderParent.resolve(internalFolderName).toAbsolutePath().toString();
    }

    public static Path createFolder(String folderFullPath) throws SyncDuoException {
        Path folder = Paths.get(folderFullPath);
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new SyncDuoException("创建文件夹失败 %s".formatted(folderFullPath), e);
        }
        return folder;
    }

    public static Path createFolder(Path folder) throws SyncDuoException {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new SyncDuoException("创建文件夹失败 %s".formatted(folder), e);
        }
        return folder;
    }

    public static void deleteFolder(Path folder) throws SyncDuoException {
        try {
            Files.deleteIfExists(folder);
        } catch (IOException e) {
            throw new SyncDuoException("删除文件夹失败 %s".formatted(folder), e);
        }
    }

    public static void walkFilesTree(
            String folderFullPath,
            SimpleFileVisitor<Path> simpleFileVisitor) throws SyncDuoException {
        Path folder = isFolderPathValid(folderFullPath);

        try {
            Files.walkFileTree(folder, simpleFileVisitor);
        } catch (IOException e) {
            throw new SyncDuoException("遍历文件失败", e);
        }
    }

    public static String getSeparator() {
        return FileSystems.getDefault().getSeparator();
    }

    public static String getUuid4(String fileFullPath) throws SyncDuoException {
        if (StringUtils.isEmpty(fileFullPath)) {
            throw new SyncDuoException("获取 uuid4 失败, 文件路径为空");
        }
        byte[] hash = DigestUtils.sha256(fileFullPath);
        return UUID.nameUUIDFromBytes(hash).toString();
    }

    public static String getUUID4(Long rootFolderId, String rootFolderFullPath, Path file)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderId, file)) {
            throw new SyncDuoException("获取 uuid4 失败, rootFolderId 或 file 为空");
        }
        if (StringUtils.isAnyBlank(rootFolderFullPath)) {
            throw new SyncDuoException("获取 uuid4 失败, rootFolderFullPath 为空");
        }
        Path rootFolder = isFolderPathValid(rootFolderFullPath);
        if (!file.startsWith(rootFolder)) {
            throw new SyncDuoException("文件路径不包含 rootFolderFullPath");
        }
        Path relativizePath = rootFolder.relativize(file);
        String relativePathString = "";
        if (relativizePath.getNameCount() > 1) {
            relativePathString = getSeparator() + relativizePath.getName(0).toString();
        }
        byte[] hash = DigestUtils.sha256(rootFolderId + relativePathString + file.getFileName());
        return UUID.nameUUIDFromBytes(hash).toString();
    }

    public static Pair<Timestamp, Timestamp> getFileCrTimeAndMTime(Path file) throws SyncDuoException {
        log.info("正在读取文件:%s 的元数据".formatted(file.toAbsolutePath()));
        if (!Files.exists(file) && Files.isRegularFile(file)) {
            throw new SyncDuoException("文件: %s 不存在".formatted(file.toAbsolutePath()));
        }
        BasicFileAttributes basicFileAttributes;
        try (FileLock ignored = tryLockWithRetries(FileChannel.open(file, StandardOpenOption.READ), true)) {
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
        Path relativizePath = rootFolder.relativize(file.getParent());
        return relativizePath.toString();
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

        try (FileLock ignored = tryLockWithRetries(FileChannel.open(file, StandardOpenOption.READ), true);
             InputStream is = Files.newInputStream(file)) {
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

        try (FileLock ignored = tryLockWithRetries(FileChannel.open(file, StandardOpenOption.READ), true)) {
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


    public static Path copyFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件复制操作. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath));

        ImmutablePair<Path, Path> pathPair = isFilePathValid(sourcePath, destPath);
        Path sourceFile = pathPair.getLeft();
        Path destFile = pathPair.getRight();
        // 保证目的地文件夹存在
        try {
            Files.createDirectories(destFile.getParent());
        } catch (IOException e) {
            throw new SyncDuoException("文件夹递归创建失败. 源文件 %s, 目的文件 %s", e);
        }
        try (FileLock ignore =
                     tryLockWithRetries(FileChannel.open(sourceFile, StandardOpenOption.READ), true);
             FileLock ignored =
                     tryLockWithRetries(FileChannel.open(destFile, StandardOpenOption.CREATE), false)) {
            return Files.copy(sourceFile, destFile);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s", e);
        }
    }

    public static Path updateFileByCopy(Path sourceFile, Path destFile) throws SyncDuoException {
        isFileValid(sourceFile);
        isFileValid(destFile);
        try (FileLock ignore =
                     tryLockWithRetries(FileChannel.open(sourceFile, StandardOpenOption.READ), true);
             FileLock ignored =
                     tryLockWithRetries(FileChannel.open(destFile, StandardOpenOption.CREATE), false)) {
            return Files.copy(sourceFile, destFile);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s", e);
        }
    }

    public static Path hardlinkFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件hardlink操作. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath));

        Path sourceFile = isFilePathValid(sourcePath);
        Path destFile = Paths.get(destPath);
        if (!sourceFile.getRoot().equals(destFile.getRoot())) {
            throw new SyncDuoException("源文件路径:%s 和目标文件路径:%s 不在一个磁盘上".formatted(sourcePath, destPath));
        }
        // 保证目的地文件夹存在
        try {
            Files.createDirectories(destFile.getParent());
        } catch (IOException e) {
            throw new SyncDuoException("文件夹递归创建失败. 源文件 %s, 目的文件 %s", e);
        }
        // hardlink file
        try (FileLock ignored =
                     tryLockWithRetries(FileChannel.open(sourceFile, StandardOpenOption.READ), true)) {
            // 执行文件 hardlink
            return Files.createLink(sourceFile, destFile);
        } catch (IOException | SyncDuoException e) {
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

    private static Path isFilePathValid(String filePath)
            throws SyncDuoException {
        if (StringUtils.isBlank(filePath)) {
            throw new SyncDuoException("文件路径:%s 为空".formatted(filePath));
        }

        Path sourceFile = Paths.get(filePath);
        if (!Files.exists(sourceFile)) {
            throw new SyncDuoException("文件路径:%s 不存在".formatted(filePath));
        }
        return sourceFile;
    }

    private static void isFileValid(Path file) throws SyncDuoException {
        if (!Files.exists(file)) {
            throw new SyncDuoException("文件路径:%s 不存在".formatted(file));
        }
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

    private static FileLock tryLockWithRetries(FileChannel fileChannel, boolean shared) throws SyncDuoException {
        FileLock lock = null;
        for (int i = 0; i < fileLockRetryCount; i++) {
            try {
                lock = fileChannel.tryLock(0, Long.MAX_VALUE, shared);
            } catch (IOException e) {
                throw new SyncDuoException("无法获取锁,文件是 %s".formatted(fileChannel));
            }
            if (ObjectUtils.isNotEmpty(lock)) {
                break;
            }
            log.info("正在重试获取文件锁.文件是 %s, 次数是 %s".formatted(fileChannel, i+1));
            LockSupport.parkNanos(fileLockWaitInterval * 1_000_000);
        }
        if (ObjectUtils.isEmpty(lock)) {
            throw new SyncDuoException("获取锁失败 %s".formatted(fileChannel));
        }
        return lock;
    }
}
