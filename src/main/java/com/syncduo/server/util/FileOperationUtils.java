package com.syncduo.server.util;

import com.syncduo.server.exception.SyncDuoException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Timestamp;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class FileOperationUtils {

    private static final ConcurrentHashMap<Path, ReentrantReadWriteLock> fileLockMap =
            new ConcurrentHashMap<>(10000);

    private static final int MAX_RETRIES = 3;

    private static final int MIN_WAIT_TIME_SECONDS = 3;

    private static final int MAX_WAIT_TIME_SECONDS = 7;

    private static final Random RANDOM = new Random();


    public static boolean endsWithSeparator(String path) throws SyncDuoException {
        if (StringUtils.isEmpty(path)) {
            throw new SyncDuoException("path is empty");
        }
        return path.endsWith(FileSystems.getDefault().getSeparator());
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
        // 检查参数
        Path folderPath = isFolderPathValid(folder);
        // Use walkFileTree to recursively delete files and directories
        try {
            Files.walkFileTree(folderPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return super.visitFile(file, attrs);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return super.postVisitDirectory(dir, exc);
                }
            });
        } catch (IOException e) {
            throw new SyncDuoException("遍历删除文件夹失败 ", e);
        }
        log.info("All folders and files have been deleted successfully.");
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

    // 拼接字符串 <rootFolderId><relativePath><fileFullName>
    // 生成 UUID4
    public static String getUUID4(Long rootFolderId, String rootFolderFullPath, Path file)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderId, file)) {
            throw new SyncDuoException("获取 uuid4 失败, rootFolderId 或 file 为空");
        }
        Path rootFolder = isFolderPathValid(rootFolderFullPath);
        if (!file.startsWith(rootFolder)) {
            throw new SyncDuoException("文件路径不包含 rootFolderFullPath");
        }
        String relativizePath = getRelativePath(rootFolderFullPath, file);
        byte[] hash = DigestUtils.sha256(rootFolderId + relativizePath + file.getFileName());
        return UUID.nameUUIDFromBytes(hash).toString();
    }

    public static String getUUID4(Long rootFolderId, String fileRelativePath, String fileFullName)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderId)) {
            throw new SyncDuoException("获取 uuid4 失败, rootFolderId 为空");
        }
        if (StringUtils.isAnyBlank(fileRelativePath, fileFullName)) {
            throw new SyncDuoException("获取 uuid4 失败, fileRelativePath 或 fileFullName 为空");
        }
        byte[] hash = DigestUtils.sha256(rootFolderId + fileRelativePath + fileFullName);
        return UUID.nameUUIDFromBytes(hash).toString();
    }

    public static Pair<Timestamp, Timestamp> getFileCrTimeAndMTime(Path file) throws SyncDuoException {
        log.info("正在读取文件:{} 的元数据", file.toAbsolutePath());
        if (!Files.exists(file) && Files.isRegularFile(file)) {
            throw new SyncDuoException("文件: %s 不存在".formatted(file.toAbsolutePath()));
        }
        BasicFileAttributes basicFileAttributes;
        ReentrantReadWriteLock lock = null;
        try {
            lock = tryLockWithRetries(file, true);
            basicFileAttributes = Files.readAttributes(file, BasicFileAttributes.class);
            long createTimeStamp = basicFileAttributes.creationTime().toMillis();
            long lastModifiedTimeStamp = basicFileAttributes.lastModifiedTime().toMillis();
            return new ImmutablePair<>(new Timestamp(createTimeStamp), new Timestamp(lastModifiedTimeStamp));
        } catch (SyncDuoException | IOException e) {
            throw new SyncDuoException("无法读取文件:%s 的元数据", e);
        } finally {
            if (ObjectUtils.isNotEmpty(lock)) {
                lock.readLock().unlock();
            }
        }
    }

    public static String getRelativePath(String rootFolderPath, Path file) throws SyncDuoException {
        // 检查 rootFolderPath 参数
        Path rootFolder = isFolderPathValid(rootFolderPath);
        if (ObjectUtils.anyNull(file)) {
            throw new SyncDuoException("无法计算相对路径, file 为空");
        }
        if (!file.startsWith(rootFolder)) {
            throw new SyncDuoException("文件路径不包含 rootFolderFullPath");
        }
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

        ReentrantReadWriteLock lock = null;
        try {
            lock = tryLockWithRetries(file, true);
            try (InputStream is = Files.newInputStream(file)) {
                return DigestUtils.md5Hex(is);
            }
        } catch (SyncDuoException | IOException e) {
            throw new SyncDuoException("无法读取文件:%s 的 MD5 checksum".formatted(file.toAbsolutePath()));
        } finally {
            if (ObjectUtils.isNotEmpty(lock)) {
                lock.readLock().unlock();
            }
        }
    }

    public static Path copyFile(String sourcePath, String destPath) throws SyncDuoException {
        log.info("执行文件复制操作. 源文件 {}, 目的文件 {}", sourcePath, destPath);

        // 检查源文件是否存在
        Path sourceFile = isFilePathValid(sourcePath);
        // 获取 destFile 的 Path 对象, 注意此时 destFile 不一定存在于文件系统上
        Path destFile = Path.of(destPath);
        if (!Files.exists(destFile)) {
            // 保证目的地文件夹存在
            try {
                Files.createDirectories(destFile.getParent());
            } catch (IOException e) {
                throw new SyncDuoException("文件夹递归创建失败. 源文件 %s, 目的文件 %s".formatted(sourcePath, destPath), e);
            }
            // 创建一个空的文件, 用于获取 write lock
            try {
                Files.createFile(destFile);
            } catch (IOException e) {
                throw new SyncDuoException("创建空的文件 %s 失败".formatted(destFile), e);
            }
        }
        // 初始化锁
        ReentrantReadWriteLock sourceLock = null;
        ReentrantReadWriteLock destLock = null;
        try {
            sourceLock = tryLockWithRetries(sourceFile, true);
            destLock = tryLockWithRetries(destFile, false);
            return Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s".formatted(sourceFile, destFile), e);
        } finally {
            if (ObjectUtils.isNotEmpty(sourceLock)) {
                sourceLock.readLock().unlock();
            }
            if (ObjectUtils.isNotEmpty(destLock)) {
                destLock.writeLock().unlock();
            }
        }
    }

    public static Path updateFileByCopy(Path sourceFile, Path destFile) throws SyncDuoException {
        // 检查参数
        isFileValid(sourceFile);
        isFileValid(destFile);

        ReentrantReadWriteLock sourceLock = null;
        ReentrantReadWriteLock destLock = null;
        try {
            sourceLock = tryLockWithRetries(sourceFile, true);
            destLock = tryLockWithRetries(destFile, false);
            return Files.copy(sourceFile, destFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件复制失败. 源文件 %s, 目的文件 %s".formatted(sourceFile, destFile), e);
        } finally {
            if (ObjectUtils.isNotEmpty(sourceLock)) {
                sourceLock.readLock().unlock();
            }
            if (ObjectUtils.isNotEmpty(destLock)) {
                destLock.writeLock().unlock();
            }
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
        ReentrantReadWriteLock lock = null;
        try {
            // 获取读锁
            lock = tryLockWithRetries(sourceFile, true);
            // 执行文件 hardlink
            return Files.createLink(destFile, sourceFile);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("文件hardlink失败. 源文件 %s, 目的文件 %s".formatted(sourceFile, destFile), e);
        } finally {
            if (ObjectUtils.isNotEmpty(lock)) {
                lock.readLock().unlock();
            }
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

    public static ReentrantReadWriteLock tryLockWithRetries(Path file, boolean shared) throws SyncDuoException {
        // 判断锁类型
        String lockType = shared ? "Read (Shared)" : "Write (Exclusive)";
        // 获取锁, 没有则初始化
        ReentrantReadWriteLock lock = fileLockMap.computeIfAbsent(file, o -> new ReentrantReadWriteLock());
        int retryCount = 0;
        try {
            while (retryCount < MAX_RETRIES) {
                int waitTime = RANDOM.nextInt(MIN_WAIT_TIME_SECONDS, MAX_WAIT_TIME_SECONDS + 1);
                boolean locked;
                if (shared) {
                    locked = lock.readLock().tryLock(waitTime, TimeUnit.SECONDS);
                } else {
                    locked = lock.writeLock().tryLock(waitTime, TimeUnit.SECONDS);
                }
                if (locked) {
                    log.info("{} Lock acquired on attempt {}", lockType, retryCount);
                    return lock;
                } else {
                    log.info("{} Lock attempt failed, retrying in {} seconds...", lockType, waitTime);
                    retryCount++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("线程被打断");
        } catch (NullPointerException e) {
            throw new SyncDuoException(
                    "Exception occurred while attempting to acquire %s file lock".formatted(lockType), e);
        }
        throw new SyncDuoException("获取锁超时");
    }
}
