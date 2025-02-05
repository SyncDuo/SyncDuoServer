package com.syncduo.server.controller;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.dto.http.filesystem.FileSystemResponse;
import com.syncduo.server.model.dto.http.filesystem.Folder;
import com.syncduo.server.util.FileOperationUtils;
import com.syncduo.server.util.SystemInfoUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

import java.nio.file.Paths;
import java.util.Collections;
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
            return FileSystemResponse.onSuccess("getHostName Success", hostName);
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
            List<Path> subfolders = FileOperationUtils.getSubFolders(path);
            if (CollectionUtils.isEmpty(subfolders)) {
                return FileSystemResponse.onSuccess("no subfolders");
            }
            // 最相似的文件夹, 放在最前面
            subfolders.sort(Comparator.comparingInt(subfolder -> getSimilarityScore(subfolder, path)));
            Collections.reverse(subfolders);
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
    private static int getSimilarityScore(Path subfolder, String inputPath) {
        // Return the number of matching characters from the start of both paths
        int score = 0;
        String subfolderName = subfolder.toString().toLowerCase();
        inputPath = inputPath.toLowerCase();
        // Min length
        int minLength = Math.min(subfolderName.length(), inputPath.length());
        // Compare both the input path and the subfolder name, character by character
        for (int i = 0; i < minLength; i++) {
            if (subfolderName.charAt(i) == inputPath.charAt(i)) {
                score++;
            } else {
                break;
            }
        }

        return score;
    }
}
