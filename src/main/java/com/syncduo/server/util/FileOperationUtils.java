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


    public static boolean endsWithSeparator(String path) throws SyncDuoException {
        if (StringUtils.isEmpty(path)) {
            throw new SyncDuoException("path is empty");
        }
        return path.endsWith(FileSystems.getDefault().getSeparator());
    }

    public static Path concateStringToPath(
            String folderPath,
            String relativePath,
            String fileName,
            String fileExtension) throws SyncDuoException {
        String filePath = concatePathString(folderPath, relativePath, fileName, fileExtension);
        return isFilePathValid(filePath);
    }

    public static String concatePathString(
            String folderPath,
            String relativePath,
            String fileName,
            String fileExtension) throws SyncDuoException {
        if (StringUtils.isAnyBlank(folderPath, relativePath, fileName)) {
            throw new SyncDuoException("folderPath, relativePath, fileName 存在空值");
        }
        String filePath = folderPath + relativePath + fileName;
        if (StringUtils.isNotBlank(fileExtension)) {
            filePath = filePath + "." + fileExtension;
        }
        return filePath;
    }

    public static Path createContentFolder(
            String sourceFolderFullPath,
            String contentFolderFullPath) throws SyncDuoException {
        Path sourceFolder = isFolderPathValid(sourceFolderFullPath);
        Path contentFolderParent = isParentFolderPathValid(contentFolderFullPath);
        Path contentFolder = contentFolderParent.resolve(sourceFolder.getFileName());
        if (Files.exists(contentFolder)) {
            return contentFolder;
        }
        return createFolder(contentFolder);
    }

    public static String getInternalFolderFullPath(String sourceFolderFullPath) throws SyncDuoException {
        Path sourceFolder = isFolderPathValid(sourceFolderFullPath);
        Path sourceFolderParent = sourceFolder.getParent();
        String internalFolderName = "." + sourceFolder.getFileName();

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

    public static String getPathSeparator() {
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
            relativePathString = getPathSeparator() + relativizePath.getName(0).toString();
        }
        byte[] hash = DigestUtils.sha256(rootFolderId + relativePathString + file.getFileName());
        return UUID.nameUUIDFromBytes(hash).toString();
    }

    public static Pair<Timestamp, Timestamp> getFileCrTimeAndMTime(Path file) throws SyncDuoException {
        log.info("正在读取文件:{} 的元数据", file.toAbsolutePath());
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
        return StringUtils.isEmpty(relativizePath.toString()) ?
                FileOperationUtils.getPathSeparator() :
                FileOperationUtils.getPathSeparator() + relativizePath;
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
        log.info("正在读取文件:{} 的 MD5 Checksum", file.toAbsolutePath());

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


    public static Path copyFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件复制操作. 源文件 {}, 目的文件 {}", sourcePath, destPath);

        // 检查源文件是否存在
        Path sourceFile = isFilePathValid(sourcePath);
        // 获取 destFile 的 Path 对象, 注意此时 destFile 不一定存在于文件系统上
        Path destFile = Paths.get(destPath);
        // 保证目的地文件夹存在
        try {
            Files.createDirectories(destFile.getParent());
        } catch (IOException e) {
            throw new SyncDuoException("文件夹递归创建失败. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath), e);
        }
        // 打开目的地文件, 默认不存在则新建
        try (FileLock ignore = tryLockWithRetries(
                FileChannel.open(sourceFile, StandardOpenOption.READ),
                true);
             FileLock ignored = tryLockWithRetries(
                     FileChannel.open(destFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                     false)) {
            return Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s".formatted(sourceFile, destFile), e);
        }
    }

    public static Path updateFileByCopy(Path sourceFile, Path destFile) throws SyncDuoException {
        isFileValid(sourceFile);
        isFileValid(destFile);
        try (FileLock ignore = tryLockWithRetries(
                FileChannel.open(sourceFile, StandardOpenOption.READ),
                true);
             FileLock ignored = tryLockWithRetries(
                     FileChannel.open(destFile, StandardOpenOption.WRITE),
                     false)) {
            return Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s".formatted(sourceFile, destFile), e);
        }
    }

    public static Path hardlinkFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件hardlink操作. 源文件 {}, 目的文件 {}", sourcePath, destPath);

        Path sourceFile = isFilePathValid(sourcePath);
        Path destFile = Paths.get(destPath);
        if (!sourceFile.getRoot().equals(destFile.getRoot())) {
            throw new SyncDuoException("源文件路径:%s 和目标文件路径:%s 不在一个磁盘上".formatted(sourcePath, destPath));
        }
        // 保证目的地文件夹存在
        try {
            Files.createDirectories(destFile.getParent());
        } catch (IOException e) {
            throw new SyncDuoException("文件夹递归创建失败. 源文件 %s, 目的文件 %s".formatted(sourceFile, destFile), e);
        }
        // hardlink file
        try (FileLock ignored =
                     tryLockWithRetries(FileChannel.open(sourceFile, StandardOpenOption.READ), true)) {
            // 执行文件 hardlink
            return Files.createLink(destFile, sourceFile);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件hardlink失败. 源文件 %s, 目的文件 %s".formatted(sourceFile, destFile), e);
        }
    }

    public static Path isFilePathValid(String filePath)
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

    public static boolean isFilePathExist(String filePath)
            throws SyncDuoException {
        if (StringUtils.isBlank(filePath)) {
            throw new SyncDuoException("文件路径:%s 为空".formatted(filePath));
        }
        Path sourceFile = Paths.get(filePath);
        return Files.exists(sourceFile);
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

    public static Path isFolderPathValid(Path folderPath) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderPath)) {
            throw new SyncDuoException("folderPath 为空");
        }
        if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
            return folderPath;
        } else {
            throw new SyncDuoException("文件夹路径:%s 不存在或不是文件夹".formatted(folderPath));
        }
    }

    public static Path isParentFolderPathValid(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("文件夹路径为空");
        }
        Path folderPath = Paths.get(path);
        return isFolderPathValid(folderPath.getParent());
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
            log.info("正在重试获取文件锁.文件是 {}, 次数是 {}", fileChannel, i+1);
            LockSupport.parkNanos(fileLockWaitInterval * 1_000_000);
        }
        if (ObjectUtils.isEmpty(lock)) {
            throw new SyncDuoException("获取锁失败 %s".formatted(fileChannel));
        }
        return lock;
    }
}
