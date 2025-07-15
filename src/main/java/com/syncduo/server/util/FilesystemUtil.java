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
    /**
     * Splits a full file path into srcFs and srcRemote.
     * srcFs is the top-level base path (e.g., /home/user/source)
     * srcRemote is the relative path from srcFs to the file.
     *
     * @param baseFolderString The base folder you want to treat as srcFs.
     * @param fullPathString   The full path to the source file.
     * @return String array: [0] = srcFs, [1] = srcRemote
     */
    public static String splitPath(String baseFolderString, String fullPathString) throws SyncDuoException {

        Path baseFolder = FilesystemUtil.isFolderPathValid(baseFolderString);
        Path base = baseFolder.normalize();
        Path fullPath = FilesystemUtil.isFilePathValid(fullPathString);
        Path full = fullPath.normalize();

        if (!full.startsWith(base)) {
            throw new SyncDuoException("splitPath failed. Full path is not under base folder." +
                    "fullPathString is %s.".formatted(fullPathString) +
                    "baseFolderString is %s.".formatted(baseFolderString));
        }

        try {
            Path relative = base.relativize(full);
            return relative.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            throw new SyncDuoException("splitPath failed. fullPathString can't be relativize." +
                    "fullPathString is %s.".formatted(fullPathString) +
                    "baseFolderString is %s.".formatted(baseFolderString));
        }
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

    public static Path isFilePathValid(String filePath)
            throws SyncDuoException {
        if (StringUtils.isBlank(filePath)) {
            throw new SyncDuoException("isFilePathValid failed. filePath is null");
        }
        Path sourceFile = Paths.get(filePath);
        if (Files.exists(sourceFile)) {
            return sourceFile;
        } else {
            throw new SyncDuoException(("isFilePathValid failed. filePath not exist. " +
                    "filePath is %s").formatted(filePath));
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

    public static List<Path> getSubFolders(String path) throws SyncDuoException {
        if (StringUtils.isBlank(path)) {
            throw new SyncDuoException("getSubfolders failed. path is null");
        }
        Path folder = Paths.get(path);
        // 如果输入的路径不存在, 则寻找它的父路径, 如果父路径也不存在, 则抛出异常
        if (!Files.exists(folder) || !Files.isDirectory(folder)) {
            folder = isFolderPathValid(folder.getParent().toAbsolutePath().toString());
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
}
