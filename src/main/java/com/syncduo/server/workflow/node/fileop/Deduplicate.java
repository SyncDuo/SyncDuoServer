package com.syncduo.server.workflow.node.fileop;

import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JsonUtil;
import com.syncduo.server.workflow.core.annotaion.Node;
import com.syncduo.server.workflow.core.model.base.BaseNode;
import com.syncduo.server.workflow.core.model.execution.NodeResult;
import com.syncduo.server.workflow.core.model.execution.FlowContext;
import com.syncduo.server.workflow.node.registry.FieldRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Node(
        name = "deduplicate",
        description = "对目录内的文件去重",
        group = "filesystem",
        inputParams = {FieldRegistry.SOURCE_DIRECTORY},
        outputParams = {FieldRegistry.DEDUPLICATE_FILES}
)
@Slf4j
public class Deduplicate extends BaseNode {

    @Override
    public NodeResult execute(FlowContext context) {
        String sourceDirectory = FieldRegistry.getString(FieldRegistry.SOURCE_DIRECTORY, context);
        FilesystemUtil.isFolderPathValid(sourceDirectory);
        try {
            List<String> result = removeDuplicateFiles(sourceDirectory);
            return NodeResult.success(Map.of(FieldRegistry.DEDUPLICATE_FILES, result));
        } catch (IOException e) {
            return NodeResult.failed(new BusinessException("去除重复文件失败", e).toString());
        }
    }

    /**
     * 删除重复文件，保留创建时间最早的文件
     * @param directoryPath 目录路径
     */
    public static List<String> removeDuplicateFiles(String directoryPath) throws IOException {
        if (directoryPath == null || directoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("目录路径不能为空");
        }

        Path dirPath = Paths.get(directoryPath);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            throw new IllegalArgumentException("目录不存在或不是有效目录: " + directoryPath);
        }

        // 存储校验和到文件列表的映射
        Map<String, List<FileInfo>> checksumMap = new HashMap<>();

        log.debug("开始扫描目录: " + directoryPath);

        // 遍历目录中的所有文件
        Files.walk(dirPath)
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    try {
                        String checksum = calculateChecksum(filePath);
                        FileInfo fileInfo = new FileInfo(filePath, getCreationTime(filePath));

                        // 将文件信息添加到对应的校验和分组中
                        checksumMap.computeIfAbsent(checksum, k -> new ArrayList<>()).add(fileInfo);

                    } catch (Exception e) {
                        log.debug("处理文件时出错: " + filePath + " - " + e.getMessage());
                    }
                });

        log.debug("扫描完成，发现 " + checksumMap.size() + " 个不同的文件校验和");

        // 处理重复文件
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<String, List<FileInfo>> entry : checksumMap.entrySet()) {
            List<FileInfo> fileList = entry.getValue();

            if (fileList.size() > 1) {
                log.debug("\n发现 " + fileList.size() + " 个重复文件 (校验和: " + entry.getKey() + "):");

                // 按创建时间排序，最早的排在前面
                fileList.sort(Comparator.comparing(f -> f.creationTime));

                // 保留最早创建的文件
                FileInfo keepFile = fileList.get(0);
                log.debug("  保留: " + keepFile.path + " (创建时间: " + keepFile.creationTime + ")");

                // 删除其他重复文件
                for (int i = 1; i < fileList.size(); i++) {
                    FileInfo deleteFile = fileList.get(i);
                    try {
                        Files.deleteIfExists(deleteFile.path);
                        result.add(deleteFile.path.toString());
                        log.debug("  删除: " + deleteFile.path + " (创建时间: " + deleteFile.creationTime + ")");
                    } catch (IOException e) {
                        log.debug("  删除文件失败: " + deleteFile.path + " - " + e.getMessage());
                    }
                }
            }
        }

        log.debug("\n处理完成，总共删除了 " + result.size() + " 个重复文件");
        return result;
    }

    /**
     * 计算文件的MD5校验和
     */
    private static String calculateChecksum(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");

        try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }

        // 将字节数组转换为十六进制字符串
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }

    /**
     * 获取文件的创建时间
     */
    private static long getCreationTime(Path filePath) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        return attrs.creationTime().toMillis();
    }

    /**
     * 文件信息类
     */
    private static class FileInfo {
        final Path path;
        final Long creationTime; // 创建时间戳（毫秒）

        FileInfo(Path path, Long creationTime) {
            this.path = path;
            this.creationTime = creationTime;
        }
    }
}
