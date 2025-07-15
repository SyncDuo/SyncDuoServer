package com.syncduo.server.controller;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.api.filesystem.FileSystemResponse;
import com.syncduo.server.model.api.filesystem.Folder;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.SystemInfoUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/filesystem")
@CrossOrigin
@Slf4j
public class FileSystemAccessController {

    @GetMapping("/get-hostname")
    public FileSystemResponse getHostName() {
        try {
            String hostName = SystemInfoUtil.getHostName();
            return FileSystemResponse.onSuccess("获取 hostname 成功", hostName);
        } catch (SyncDuoException e) {
            return FileSystemResponse.onError(e.getMessage());
        }
    }

    @GetMapping("/get-subfolders")
    public FileSystemResponse getSubfolders(@RequestParam("path") String path) {
        if (StringUtils.isBlank(path)) {
            return FileSystemResponse.onError("path is null");
        }
        try {
            List<Path> subfolders = FilesystemUtil.getSubFolders(path);
            if (CollectionUtils.isEmpty(subfolders)) {
                return FileSystemResponse.onSuccess("no subfolders");
            }
            // 最相似的文件夹, 放在最前面
            Comparator<Path> similarityComparator = Comparator.comparingInt(
                    (Path subfolder) -> getSimilarityScore(subfolder.toAbsolutePath().toString(), path)).reversed();
            subfolders.sort(similarityComparator);
            List<Folder> folderList = subfolders.stream().map(Folder::new).toList();
            return FileSystemResponse.onSuccess("getSubfolders success.", folderList);
        } catch (SyncDuoException e) {
            log.debug("getFolder failed. getSubfolders failed. path is {}", path, e);
            return FileSystemResponse.onError("getSubfolders failed. path is %s".formatted(path));
        }
    }

    /**
     * Computes a similarity score between a subfolder and the input path.
     * The score is based on how much of the subfolder path matches the input path.
     *
     * @param subfolder  The subfolder path.
     * @param inputPath  The input path to compare against.
     * @return An integer score representing the similarity.
     */
    private static int getSimilarityScore(String subfolder, String inputPath) {
        String base = Paths.get(subfolder).normalize().toString().toLowerCase();
        String input = Paths.get(inputPath).normalize().toString().toLowerCase();

        int score = 0;
        int minCount = Math.min(base.length(), input.length());

        for (int i = 0; i < minCount; i++) {
            if (base.charAt(i) == input.charAt(i)) {
                score++;
            } else {
                break;
            }
        }

        return score;
    }
}
