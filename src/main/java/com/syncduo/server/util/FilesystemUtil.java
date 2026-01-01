package com.syncduo.server.util;

import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class FilesystemUtil {

    private static final String ALL_LETTER = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final Random RANDOM = new Random();

    private static final int NAME_LENGTH = 9;

    public static Path isFolderPathValid(
            String folderPathString) throws ValidationException, BusinessException {
        if (StringUtils.isBlank(folderPathString)) {
            throw new ValidationException("isFolderPathValid failed. folderPathString is null");
        }
        Path folder = Paths.get(folderPathString);
        if (Files.exists(folder) && Files.isDirectory(folder)) {
            return folder;
        } else {
            throw new BusinessException(("isFolderPathValid failed. " +
                    "folderPathString doesn't exist or is not folder." +
                    "folderPathString is %s").formatted(folderPathString));
        }
    }

    // 获取 folderPathString 下所有的文件, 并压缩到同样路径下的 zip file
    public static Path zipAllFile(String folderPathString, String prefix)
            throws ValidationException, BusinessException {
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
            throw new BusinessException("zipAllFile resolve path failed.", e);
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
            throw new BusinessException("zipAllFile failed.", e);
        }
        return zipFile;
    }
}
