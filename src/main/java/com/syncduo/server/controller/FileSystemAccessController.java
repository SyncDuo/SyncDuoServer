package com.syncduo.server.controller;

import com.syncduo.server.model.api.filesystem.Folder;
import com.syncduo.server.model.api.global.SyncDuoHttpResponse;
import com.syncduo.server.util.FilesystemUtil;
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
@CrossOrigin(originPatterns = "*")
@Slf4j
public class FileSystemAccessController {

    @GetMapping("/get-hostname")
    public SyncDuoHttpResponse<String> getHostName() {
        return SyncDuoHttpResponse.success(SystemInfoUtil.getHostName());
    }

    @GetMapping("/get-subfolders")
    public SyncDuoHttpResponse<List<Folder>> getSubfolders(@RequestParam("path") String path) {
        if (StringUtils.isBlank(path)) {
            return SyncDuoHttpResponse.success(Collections.emptyList(), "path is empty");
        }
        List<Path> subfolders = FilesystemUtil.getSubFolders(path);
        if (CollectionUtils.isEmpty(subfolders)) {
            return SyncDuoHttpResponse.success(Collections.emptyList(), "path does not contain subfolders");
        }
        // 最相似的文件夹, 放在最前面
        Comparator<Path> similarityComparator = Comparator.comparingInt(
                (Path subfolder) -> getSimilarityScore(subfolder.toAbsolutePath().toString(), path)).reversed();
        subfolders.sort(similarityComparator);
        List<Folder> folderList = subfolders.stream().map(Folder::new).toList();
        return SyncDuoHttpResponse.success(folderList);
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
