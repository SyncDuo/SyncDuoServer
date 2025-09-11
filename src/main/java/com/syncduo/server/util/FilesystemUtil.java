package com.syncduo.server.util;

import com.syncduo.server.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class FilesystemUtil {

    private static final String ALL_LETTER = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final Random RANDOM = new Random();

    private static final int MAX_ATTEMPTS = 5;

    private static final int NAME_LENGTH = 9;

    /**
     * Splits a full file path into srcFs and srcRemote.
     * srcFs is the top-level base path (e.g., /home/user/source)
     * srcRemote is the relative path from srcFs to the file.
     *
     * @param baseFolderString The base folder you want to treat as srcFs.
     * @param fullPathString   The full path to the source file.
     * @return String fullPathString minus baseFolderString
     */
    public static String splitPath(
            String baseFolderString, String fullPathString) throws ValidationException {
        // 参数检查
        Path baseFolder = FilesystemUtil.isFolderPathValid(baseFolderString);
        Path base = baseFolder.normalize();
        Path fullPath = FilesystemUtil.isFilePathValid(fullPathString);
        Path full = fullPath.normalize();
        // 判断 fullPathString 是否包含在 baseFolderString 中
        if (!full.startsWith(base)) {
            throw new ValidationException("splitPath failed. " +
                    "fullPathString doesn't contain baseFolderString." +
                    "fullPathString is %s.".formatted(fullPathString) +
                    "baseFolderString is %s.".formatted(baseFolderString));
        }
        // 获取 fullPathString - baseFolderString
        try {
            Path relative = base.relativize(full);
            return relative.toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            throw new ValidationException("splitPath failed. fullPathString can't be relativize." +
                    "fullPathString is %s.".formatted(fullPathString) +
                    "baseFolderString is %s.".formatted(baseFolderString));
        }
    }

    public static List<Long> getFolderInfo(String path)
            throws ValidationException, ResourceNotFoundException, FileOperationException {
        // 参数检查
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
            throw new FileOperationException("getFolderInfo failed.", e);
        }

        return Arrays.asList(fileCount.get(), subFolderCount.get(), totalSize.get());
    }

    public static Path isFilePathValid(String filePathString)
            throws ValidationException, ResourceNotFoundException {
        if (StringUtils.isBlank(filePathString)) {
            throw new ValidationException("isFilePathValid failed. filePathString is null");
        }
        Path file = Paths.get(filePathString);
        if (Files.exists(file)) {
            return file;
        } else {
            throw new ResourceNotFoundException("isFilePathValid failed. " +
                    "filePathString:%s not exist. ".formatted(filePathString));
        }
    }

    public static Path isFolderPathValid(
            String folderPathString) throws ValidationException, ResourceNotFoundException {
        if (StringUtils.isBlank(folderPathString)) {
            throw new ValidationException("isFolderPathValid failed. folderPathString is null");
        }
        Path folder = Paths.get(folderPathString);
        if (Files.exists(folder) && Files.isDirectory(folder)) {
            return folder;
        } else {
            throw new ResourceNotFoundException(("isFolderPathValid failed. " +
                    "folderPathString doesn't exist or is not folder." +
                    "folderPathString is %s").formatted(folderPathString));
        }
    }

    public static List<Path> getSubFolders(
            String folderPathString) throws ValidationException, FileOperationException {
        if (StringUtils.isBlank(folderPathString)) {
            return Collections.emptyList();
        }
        Path folder = Paths.get(folderPathString);
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
            throw new FileOperationException(("getSubfolders failed. " +
                    "folderPathString is %s").formatted(folderPathString),
                    e);
        }
        return result;
    }

    public static String createRandomEnglishFolder(
            String parentFolderPathString) throws ValidationException, FileOperationException {
        Path parentFolder = isFolderPathValid(parentFolderPathString);
        // 拼接路径
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            // 获取随机文件夹名称
            StringBuilder sb = new StringBuilder(NAME_LENGTH);
            for (int j = 0; j < NAME_LENGTH; j++) {
                int index = RANDOM.nextInt(ALL_LETTER.length());
                sb.append(ALL_LETTER.charAt(index));
            }
            Path newFolderTmp = parentFolder.resolve(sb.toString()).normalize();
            if (Files.exists(newFolderTmp)) {
                continue;
            }
            // 创建文件夹
            try {
                Path newFolder = Files.createDirectory(newFolderTmp);
                return newFolder.toAbsolutePath().toString();
            } catch (IOException e) {
                throw new FileOperationException("createRandomEnglishFolder failed. ", e);
            }
        }
        throw new BusinessException("createRandomEnglishFolder failed after all attempts.");
    }

    public static List<Path> getAllFile(Path folder)
            throws FileOperationException {
        try(Stream<Path> list = Files.list(folder)) {
            return list.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            throw new FileOperationException("getAllFile failed.", e);
        }
    }

    public static Path zipAllFile(String folderPathString, String prefix)
            throws ValidationException, ResourceNotFoundException, FileOperationException {
        // 检查参数
        Path folder = isFolderPathValid(folderPathString);
        // 获取随机zip文件名称
        StringBuilder sb = new StringBuilder(prefix + "-");
        for (int i = 0; i < NAME_LENGTH; i++) {
            int index = RANDOM.nextInt(ALL_LETTER.length());
            sb.append(ALL_LETTER.charAt(index));
        }
        Path zipFile;
        try {
            zipFile = folder.resolve(sb.append(".zip").toString());
        } catch (Exception e) {
            throw new ResourceNotFoundException("zipAllFile resolve path failed.", e);
        }
        // 遍历所有文件和文件夹
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // 跳过ZIP文件自身
                    if (dir.equals(zipFile)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // 计算相对路径
                    String zipEntryName = folder.relativize(dir).toString().replace("\\", "/");
                    if (!zipEntryName.isEmpty()) {
                        zos.putNextEntry(new ZipEntry(zipEntryName + "/"));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 跳过ZIP文件自身
                    if (file.equals(zipFile)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    // 计算相对路径
                    String zipEntryName = folder.relativize(file).toString().replace("\\", "/");
                    // 处理文件：写入ZIP条目
                    zos.putNextEntry(new ZipEntry(zipEntryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new FileOperationException("zipAllFile failed.", e);
        }
        return zipFile;
    }

    /**
     * 删除指定路径的文件夹及其所有内容
     * @param folderPathString 要删除的文件夹路径
     * @throws FileOperationException 如果删除过程中发生错误
     */
    public static void deleteFolder(String folderPathString, boolean includeSelf)
            throws ValidationException, ResourceNotFoundException, FileOperationException {
        Path folderPath = isFolderPathValid(folderPathString);
        // 使用Files.walkFileTree遍历并删除文件夹内容
        try {
            Files.walkFileTree(folderPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    // 删除文件
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    // 删除目录（在访问完目录内容后执行）
                    if (!includeSelf && dir.equals(folderPath)) {
                        // 遇到 self 则跳过
                        return FileVisitResult.CONTINUE;
                    }
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new FileOperationException("deleteFolder failed. ", e);
        }
    }
}
