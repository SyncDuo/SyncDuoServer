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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class FilesystemUtil {

    private static final ConcurrentHashMap<Path, ReentrantReadWriteLock> fileLockMap =
            new ConcurrentHashMap<>(10000);

    private static final int MAX_RETRIES = 3;

    private static final int MIN_WAIT_TIME_SECONDS = 3;

    private static final int MAX_WAIT_TIME_SECONDS = 7;

    private static final Random RANDOM = new Random();

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final FileSystem FILE_SYSTEM = FileSystems.getDefault();

    public static List<Path> getAllFileInFolder(String folderPath) throws SyncDuoException {
        List<Path> fileList = new ArrayList<>();
        Path startPath = isFolderPathValid(folderPath);

        // Walk the file tree and collect files
        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    fileList.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Handle the case where a file or directory could not be accessed
                    log.error("Error accessing file: {}", file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new SyncDuoException("listFilesInFolder failed. folderPath is %s".formatted(folderPath), e);
        }

        return fileList;
    }


    public static List<Long> getFolderInfo(String path) throws SyncDuoException {
        Path folder = FilesystemUtil.isFolderPathValid(path);
        // 初始化变量
        AtomicLong fileCount = new AtomicLong(0);
        AtomicLong subFolderCount = new AtomicLong(0);
        AtomicLong totalSize = new AtomicLong(0);
        // 遍历统计
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    fileCount.incrementAndGet();
                    totalSize.addAndGet(attrs.size());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    subFolderCount.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new SyncDuoException("getFolderInfo failed. exception is " + e.getMessage());
        }

        return Arrays.asList(fileCount.get(), subFolderCount.get(), totalSize.get());
    }

    public static boolean endsWithSeparator(String path) throws SyncDuoException {
        if (StringUtils.isEmpty(path)) {
            throw new SyncDuoException("get path separator failed, path is empty");
        }
        return path.endsWith(getPathSeparator());
    }

    public static Path createContentFolder(
            String sourceFolderFullPath,
            String contentFolderFullPath) throws SyncDuoException {
        isFolderPathValid(sourceFolderFullPath);
        isParentFolderPathValid(contentFolderFullPath);
        Path contentFolder = Path.of(contentFolderFullPath);
        if (Files.exists(contentFolder)) {
            log.warn("the content folder already exist");
            return contentFolder;
        }
        return createFolder(contentFolder);
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

    public static void deleteFile(Path file) throws SyncDuoException {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new SyncDuoException("deleteFile failed. file is %s.".formatted(file), e);
        }
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

    // 拼接字符串 <folderId><relativePath><fileFullName>
    // 生成 sha256 hash
    public static String getUniqueHash(Long folderId, String folderFullPath, Path file)
            throws SyncDuoException {
        if (ObjectUtils.anyNull(folderId, file)) {
            throw new SyncDuoException("getUniqueHash failed, folderId or file 为空");
        }
        Path rootFolder = isFolderPathValid(folderFullPath);
        if (!file.startsWith(rootFolder)) {
            throw new SyncDuoException("getUniqueHash failed. file's path doesn't contain folderFullPath." +
                    "folderFullPath is %s, file is %s".formatted(folderFullPath, file));
        }
        String relativizePath = getRelativePath(folderFullPath, file);
        byte[] hash = DigestUtils.sha256(folderId + relativizePath + file.getFileName());
        return hashBytesToString(hash);
    }

    private static String hashBytesToString (byte[] hashBytes) {
        // Convert the byte array to a hexadecimal string and return it
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b)); // Format as two-digit hex
        }
        return hexString.toString();
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
                FilesystemUtil.getPathSeparator() :
                FilesystemUtil.getPathSeparator() + relativizePath;
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

    public static boolean isFolderPathExist(String path) throws SyncDuoException {
        if (ObjectUtils.isEmpty(path)) {
            throw new SyncDuoException("isFolderPathValid failed. folderPath is null");
        }
        Path folderPath = Paths.get(path);
        if (Files.exists(folderPath)) {
            try(DirectoryStream<Path> dirStream = Files.newDirectoryStream(folderPath)) {
                return !dirStream.iterator().hasNext();
            } catch (IOException e) {
                throw new SyncDuoException("isFolderPathExist failed. path is %s".formatted(path), e);
            }
        } else {
            return false;
        }
    }

    public static List<Path> getSubFolders(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("getSubfolders failed. path is null");
        }
        Path folder = Paths.get(path);
        // 如果输入的路径不存在, 则寻找它的父路径, 如果父路径也不存在, 则抛出异常
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            folder = isFolderPathValid(folder.getParent());
        }
        List<Path> result = new ArrayList<>(10);
        try (DirectoryStream<Path> subFolderStream = Files.newDirectoryStream(folder)) {
            for (Path subFolder : subFolderStream) {
                if (Files.isDirectory(subFolder)) {
                    result.add(subFolder);
                }
            }
        } catch (IOException e) {
            throw new SyncDuoException("getSubfolders failed. path is %s".formatted(path), e);
        }
        return result;
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

    //  返回一个 string, 格式为 randomFileName.fileExtension, 其文件名称为随机的8位长字符串, 且保证在目录下唯一
    public static String getNewFileName(String fileFullPath) throws SyncDuoException {
        Path filePath = Paths.get(fileFullPath);
        Path parentDir = filePath.getParent();
        Pair<String, String> fileNameAndExtension = FilesystemUtil.getFileNameAndExtension(filePath);
        String extension = fileNameAndExtension.getRight();

        Path newFilePath;
        do {
            String newFileName = FilesystemUtil.getRandomName() + extension;
            newFilePath = parentDir.resolve(newFileName);
        } while (Files.exists(newFilePath)); // Keep trying until no duplicate

        return newFilePath.getFileName().toString();
    }

    private static String getRandomName() {
        StringBuilder randomName = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            randomName.append(CHARACTERS.charAt(index));
        }
        return randomName.toString();
    }
}
