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

    private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();


    public static boolean endsWithSeparator(String path) throws SyncDuoException {
        if (StringUtils.isEmpty(path)) {
            throw new SyncDuoException("get path separator failed, path is empty");
        }
        return path.endsWith(getPathSeparator());
    }

    public static Path createContentFolder(
            String sourceFolderFullPath,
            String contentFolderFullPath) throws SyncDuoException {
        Path sourceFolder = isFolderPathValid(sourceFolderFullPath);
        Path contentFolderParent = isParentFolderPathValid(contentFolderFullPath);
        Path contentFolder = contentFolderParent.resolve(sourceFolder.getFileName());
        if (Files.exists(contentFolder)) {
            log.warn("the content folder already exist");
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
            throw new SyncDuoException("createFolder failed. folderFullPath is %s".formatted(folderFullPath), e);
        }
        return folder;
    }

    public static Path createFolder(Path folder) throws SyncDuoException {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new SyncDuoException("createFolder failed. folder is %s".formatted(folder), e);
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
            throw new SyncDuoException("delete folder recursively failed. folder is %s".formatted(folder), e);
        }
        log.debug("All folders and files have been deleted successfully.");
    }

    public static void walkFilesTree(
            String folderFullPath,
            SimpleFileVisitor<Path> simpleFileVisitor) throws SyncDuoException {
        Path folder = isFolderPathValid(folderFullPath);
        try {
            Files.walkFileTree(folder, simpleFileVisitor);
        } catch (IOException e) {
            throw new SyncDuoException("walkFilesTree failed. folderFullPath is %s".formatted(folderFullPath), e);
        }
    }

    public static String getPathSeparator() {
        return FILE_SYSTEM.getSeparator();
    }

    // 拼接字符串 <rootFolderId><relativePath><fileFullName>
    // 生成 UUID4
    public static String getUUID4(Long rootFolderId, String rootFolderFullPath, Path file)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderId, file)) {
            throw new SyncDuoException("getUUID4 failed, rootFolderId or file 为空");
        }
        Path rootFolder = isFolderPathValid(rootFolderFullPath);
        if (!file.startsWith(rootFolder)) {
            throw new SyncDuoException("getUUID4 failed. file's path doesn't contain rootFolderFullPath." +
                    "rootFolderFullPath is %s, file is %s".formatted(rootFolderFullPath, file));
        }
        String relativizePath = getRelativePath(rootFolderFullPath, file);
        byte[] hash = DigestUtils.sha256(rootFolderId + relativizePath + file.getFileName());
        return UUID.nameUUIDFromBytes(hash).toString();
    }

    public static String getUUID4(Long rootFolderId, String fileRelativePath, String fileFullName)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(rootFolderId)) {
            throw new SyncDuoException("getUUID4 failed, rootFolderId is null");
        }
        if (StringUtils.isAnyBlank(fileRelativePath, fileFullName)) {
            throw new SyncDuoException("getUUID4 failed, fileRelativePath or fileFullName is null." +
                    "fileRelativePath is %s, fileFullName is %s".formatted(fileRelativePath, fileFullName));
        }
        byte[] hash = DigestUtils.sha256(rootFolderId + fileRelativePath + fileFullName);
        return UUID.nameUUIDFromBytes(hash).toString();
    }

    public static Pair<Timestamp, Timestamp> getFileCrTimeAndMTime(Path file) throws SyncDuoException {
        log.debug("reading file:{} 's metadata", file.toAbsolutePath());
        if (!Files.exists(file) && Files.isRegularFile(file)) {
            throw new SyncDuoException("file: %s doesn't exist".formatted(file.toAbsolutePath()));
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
            throw new SyncDuoException("getFileCrTimeAndMTime failed, file is %s".formatted(file), e);
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
            throw new SyncDuoException("getRelativePath failed, file is null");
        }
        if (!file.startsWith(rootFolder)) {
            throw new SyncDuoException("getRelativePath failed, file's path doesn't contain rootFolderPath." +
                    "file is %s, rootFolderPath is %s".formatted(file, rootFolderPath));
        }
        Path relativizePath = rootFolder.relativize(file.getParent());
        return StringUtils.isEmpty(relativizePath.toString()) ?
                FileOperationUtils.getPathSeparator() :
                FileOperationUtils.getPathSeparator() + relativizePath;
    }

    public static Pair<String, String> getFileNameAndExtension(Path file) throws SyncDuoException {
        if (ObjectUtils.isEmpty(file)) {
            throw new SyncDuoException("getFileNameAndExtension failed, file is null");
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
        log.debug("reading file:{} 's MD5 checksum", file.toAbsolutePath());

        if (!Files.exists(file) && Files.isRegularFile(file)) {
            throw new SyncDuoException("getMD5Checksum failed. file: %s doesn't exist".formatted(file));
        }

        ReentrantReadWriteLock lock = null;
        try {
            lock = tryLockWithRetries(file, true);
            try (InputStream is = Files.newInputStream(file)) {
                return DigestUtils.md5Hex(is);
            }
        } catch (SyncDuoException | IOException e) {
            throw new SyncDuoException("getMD5Checksum failed. file is %s".formatted(file));
        } finally {
            if (ObjectUtils.isNotEmpty(lock)) {
                lock.readLock().unlock();
            }
        }
    }

    public static Path copyFile(String sourcePath, String destPath) throws SyncDuoException {
        log.debug("copying file. source file is {}, dest file is {}", sourcePath, destPath);

        // 检查源文件是否存在
        Path sourceFile = isFilePathValid(sourcePath);
        // 获取 destFile 的 Path 对象, 注意此时 destFile 不一定存在于文件系统上
        Path destFile = Path.of(destPath);
        if (!Files.exists(destFile)) {
            // 保证目的地文件夹存在
            try {
                Files.createDirectories(destFile.getParent());
            } catch (IOException e) {
                throw new SyncDuoException("createDirectories failed. source file is %s, dest file is %s".
                        formatted(sourcePath, destPath), e);
            }
            // 创建一个空的文件, 用于获取 write lock
            try {
                Files.createFile(destFile);
            } catch (IOException e) {
                throw new SyncDuoException("createFile failed. file is %s".formatted(destFile), e);
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
            throw new SyncDuoException("copyFile failed. source file is %s, dest file is %s".
                    formatted(sourceFile, destFile), e);
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
            throw new SyncDuoException("updateFileByCopy failed. source file is %s, dest file is %s".
                    formatted(sourceFile, destFile), e);
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
        log.debug("doing hardlinkFile. source file is {}, dest file is {}", sourcePath, destPath);

        Path sourceFile = isFilePathValid(sourcePath);
        Path destFile = Paths.get(destPath);
        if (!sourceFile.getRoot().equals(destFile.getRoot())) {
            throw new SyncDuoException(("hardlinkFile failed. " +
                    "source file and dest file doesn't located on the same disk. " +
                    "source file is %s, dest file is %s").formatted(sourcePath, destPath));
        }
        // 保证目的地文件夹存在
        try {
            Files.createDirectories(destFile.getParent());
        } catch (IOException e) {
            throw new SyncDuoException("createDirectories failed. source file is %s, dest file is %s"
                    .formatted(sourceFile, destFile), e);
        }
        // hardlink file
        ReentrantReadWriteLock lock = null;
        try {
            // 获取读锁
            lock = tryLockWithRetries(sourceFile, true);
            // 执行文件 hardlink
            return Files.createLink(destFile, sourceFile);
        } catch (IOException | SyncDuoException e) {
            throw new SyncDuoException("hardlinkFile failed. source file is %s, dest file is %s"
                    .formatted(sourceFile, destFile), e);
        } finally {
            if (ObjectUtils.isNotEmpty(lock)) {
                lock.readLock().unlock();
            }
        }
    }

    public static Path isFilePathValid(String filePath)
            throws SyncDuoException {
        if (StringUtils.isBlank(filePath)) {
            throw new SyncDuoException("isFilePathValid failed. filePath is null");
        }
        if (isFilePathExist(filePath)) {
            return Paths.get(filePath);
        } else {
            throw new SyncDuoException("isFilePathExist failed. filePath is %s".formatted(filePath));
        }
    }

    public static boolean isFilePathExist(String filePath)
            throws SyncDuoException {
        if (StringUtils.isBlank(filePath)) {
            throw new SyncDuoException("isFilePathExist failed. filePath is null");
        }
        Path sourceFile = Paths.get(filePath);
        return Files.exists(sourceFile);
    }

    private static void isFileValid(Path file) throws SyncDuoException {
        if (!Files.exists(file)) {
            throw new SyncDuoException("isFileValid failed. file is null");
        }
    }

    public static Path isFolderPathValid(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("isFolderPathValid failed. path is null");
        }
        Path folderPath = Paths.get(path);
        if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
            return folderPath;
        } else {
            throw new SyncDuoException(("isFolderPathValid failed. path doesn't exist or is not folder." +
                    "path is %s").formatted(path));
        }
    }

    public static Path isFolderPathValid(Path folderPath) throws SyncDuoException {
        if (ObjectUtils.isEmpty(folderPath)) {
            throw new SyncDuoException("isFolderPathValid failed. folderPath is null");
        }
        if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
            return folderPath;
        } else {
            throw new SyncDuoException(("isFolderPathValid failed. path doesn't exist or is not folder. " +
                    "folderPath is %s").formatted(folderPath));
        }
    }

    public static Path isParentFolderPathValid(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("isParentFolderPathValid failed. path is null");
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
                    log.debug("{} Lock acquired on attempt {}", lockType, retryCount);
                    return lock;
                } else {
                    log.debug("{} Lock attempt failed, retrying in {} seconds...", lockType, waitTime);
                    retryCount++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("tryLockWithRetries thread get interrupted. current thread is {}", Thread.currentThread());
        } catch (NullPointerException e) {
            throw new SyncDuoException(
                    "tryLockWithRetries failed. lockType is %s, file is %s".formatted(lockType, file), e);
        }
        throw new SyncDuoException("tryLockWithRetries failed. acquire lock timeout");
    }
}
