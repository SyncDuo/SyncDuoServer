package com.syncduo.server.service.restic;

import com.syncduo.server.exception.BusinessException;
import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.exception.ValidationException;
import com.syncduo.server.model.restic.backup.BackupError;
import com.syncduo.server.model.restic.backup.BackupSummary;
import com.syncduo.server.model.restic.cat.CatConfig;
import com.syncduo.server.model.restic.global.ExitErrors;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.model.restic.init.Init;
import com.syncduo.server.model.restic.ls.Node;
import com.syncduo.server.model.restic.restore.RestoreError;
import com.syncduo.server.model.restic.restore.RestoreSummary;
import com.syncduo.server.model.restic.stats.Stats;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class ResticService {

    @Value("${syncduo.server.restic.backupPassword}")
    private String RESTIC_PASSWORD;

    @Value("${syncduo.server.restic.backupPath}")
    private String RESTIC_BACKUP_PATH;

    // create restic repository
    protected ResticExecResult<Init, ExitErrors> init() throws ValidationException {
        // 参数检查
        FilesystemUtil.isFolderPathValid(RESTIC_BACKUP_PATH);
        if (StringUtils.isBlank(RESTIC_PASSWORD)) {
            throw new ValidationException("init failed. RESTIC_PASSWORD is empty");
        }
        try {
            CommandLine commandLine = getDefaultCommandLine();
            commandLine.addArgument("init");
            CompletableFuture<ResticExecResult<Init, ExitErrors>> future = ResticParser.executeWithExitErrorsHandler(
                    RESTIC_PASSWORD,
                    RESTIC_BACKUP_PATH,
                    commandLine,
                    stdout -> JsonUtil.parseResticJsonLine(stdout, Init.getCondition(), Init.class)
            );
            return future.get();
        } catch (Exception e) {
            throw new BusinessException("init failed.", e);
        }
    }

    public ResticExecResult<BackupSummary, List<BackupError>> backup(
            String folderPathString) throws BusinessException {
        FilesystemUtil.isFolderPathValid(folderPathString);
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("backup");
        commandLine.addArgument(".");
        commandLine.addArgument("--skip-if-unchanged");
        CompletableFuture<ResticExecResult<BackupSummary, List<BackupError>>> future =
                ResticParser.executeWithWorkingDirectory(
                    RESTIC_PASSWORD,
                    RESTIC_BACKUP_PATH,
                    folderPathString,
                    commandLine,
                    stdout -> JsonUtil.parseResticJsonLine(
                            stdout, BackupSummary.getCondition(), BackupSummary.class),
                    stderr -> JsonUtil.parseResticJsonLines(
                            stderr, BackupError.getCondition(), BackupError.class)
        );
        try {
            return future.get();
        } catch (Exception e) {
            throw new BusinessException("backup failed. future get failed. ", e);
        }
    }

    public ResticExecResult<Stats, ExitErrors> stats() throws BusinessException {
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("stats");
        commandLine.addArgument("--mode");
        commandLine.addArgument("blobs-per-file");
        CompletableFuture<ResticExecResult<Stats, ExitErrors>> future = ResticParser.executeWithExitErrorsHandler(
                RESTIC_PASSWORD,
                RESTIC_BACKUP_PATH,
                commandLine,
                stdout -> JsonUtil.parseResticJsonDocument(stdout, Stats.class)
        );
        try {
            return future.get();
        } catch (Exception e) {
            throw new BusinessException("stats failed. future get failed. ", e);
        }
    }

    public ResticExecResult<CatConfig, ExitErrors> catConfig() throws BusinessException {
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("cat");
        commandLine.addArgument("config");
        CompletableFuture<ResticExecResult<CatConfig, ExitErrors>> future = ResticParser.executeWithExitErrorsHandler(
                RESTIC_PASSWORD,
                RESTIC_BACKUP_PATH,
                commandLine,
                stdout -> JsonUtil.parseResticJsonDocument(stdout, CatConfig.class)
        );
        try {
            return future.get();
        } catch (Exception e) {
            throw new BusinessException("catConfig failed. future get failed. ", e);
        }
    }

    public ResticExecResult<List<Node>, ExitErrors> ls(
            String snapshotId, String pathString) throws ValidationException, BusinessException {
        if (StringUtils.isBlank(snapshotId)) {
            throw new ValidationException("ls failed. snapshotsId is null");
        }
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("ls");
        commandLine.addArgument(snapshotId);
        if (StringUtils.isNotBlank(pathString)) {
            commandLine.addArgument(pathString);
        }
        CompletableFuture<ResticExecResult<List<Node>, ExitErrors>> future =
                ResticParser.executeWithExitErrorsHandler(
                    RESTIC_PASSWORD,
                    RESTIC_BACKUP_PATH,
                    commandLine,
                    stdout -> JsonUtil.parseResticJsonLines(stdout, Node.getCondition(), Node.class)
        );
        try {
            return future.get();
        } catch (Exception e) {
            throw new BusinessException("ls failed. future get failed. ", e);
        }
    }

    public ResticExecResult<RestoreSummary, List<RestoreError>> restore(
            String snapshotId,
            String[] pathStrings,
            String targetString) throws ValidationException, BusinessException {
        if (StringUtils.isAnyBlank(snapshotId, targetString)) {
            throw new ValidationException("restoreFile failed. snapshotsId or targetString is null");
        }
        if (ArrayUtils.isEmpty(pathStrings)) {
            throw new ValidationException("restoreFile failed. pathStrings is empty");
        }
        CommandLine restoreCommandLine = getDefaultCommandLine();
        restoreCommandLine.addArgument("restore");
        restoreCommandLine.addArgument(snapshotId);
        restoreCommandLine.addArgument("--target");
        restoreCommandLine.addArgument(targetString);
        for (String filePathString : pathStrings) {
            if (StringUtils.isBlank(filePathString)) {
                throw new ValidationException("restore failed. pathStrings has blank string");
            }
            restoreCommandLine.addArgument("--include");
            restoreCommandLine.addArgument(filePathString);
        }
        CompletableFuture<ResticExecResult<RestoreSummary, List<RestoreError>>> future = ResticParser.execute(
                RESTIC_PASSWORD,
                RESTIC_BACKUP_PATH,
                null,
                null,
                restoreCommandLine,
                stdout ->
                        JsonUtil.parseResticJsonLine(stdout, RestoreSummary.getCondition(), RestoreSummary.class),
                stderr ->
                        JsonUtil.parseResticJsonLines(stderr, RestoreError.getCondition(), RestoreError.class)
        );
        try {
            return future.get();
        } catch (Exception e) {
            throw new BusinessException("restoreFile failed. future get failed. ", e);
        }
    }

    private static CommandLine getDefaultCommandLine() {
        CommandLine commandLine = new CommandLine("restic");
        commandLine.addArgument("--json");
        return commandLine;
    }
}
