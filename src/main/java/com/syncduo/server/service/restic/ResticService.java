package com.syncduo.server.service.restic;

import com.syncduo.server.exception.SyncDuoException;
import com.syncduo.server.model.restic.backup.Error;
import com.syncduo.server.model.restic.backup.Summary;
import com.syncduo.server.model.restic.cat.CatConfig;
import com.syncduo.server.model.restic.global.ResticExecResult;
import com.syncduo.server.model.restic.init.Init;
import com.syncduo.server.model.restic.ls.Node;
import com.syncduo.server.model.restic.stats.Stats;
import com.syncduo.server.util.FilesystemUtil;
import com.syncduo.server.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
public class ResticService {

    public ResticExecResult<Init, Void> init(
            String backupStoragePath,
            String backupPassword) throws SyncDuoException {
        FilesystemUtil.isFolderPathValid(backupStoragePath);
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("init");
        ResticParser<Init, Void> resticParser = new ResticParser<>(
                backupPassword,
                backupStoragePath,
                commandLine
        );
        CompletableFuture<ResticExecResult<Init, Void>> future = resticParser.execute(
                null,
                stdout -> JsonUtil.parseResticJsonLine(stdout, Init.getCondition(), Init.class),
                null,
                null,
                null
        );
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SyncDuoException("init failed. future get failed. ", e);
        }
    }

    public ResticExecResult<Summary, Error> backup(
            String backupStoragePath,
            String backupPassword,
            String folderPathString) throws SyncDuoException {
        FilesystemUtil.isFolderPathValid(backupStoragePath);
        FilesystemUtil.isFolderPathValid(folderPathString);
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("backup");
        commandLine.addArgument(".");
        commandLine.addArgument("--skip-if-unchanged");
        ResticParser<Summary, Error> resticParser = new ResticParser<>(
                backupPassword,
                backupStoragePath,
                commandLine
        );
        CompletableFuture<ResticExecResult<Summary, Error>> future = resticParser.execute(
                folderPathString,
                stdout -> JsonUtil.parseResticJsonLine(stdout, Summary.getCondition(), Summary.class),
                null,
                stderr -> JsonUtil.parseResticJsonLines(stderr, Error.getCondition(), Error.class),
                null
        );
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SyncDuoException("backup failed. future get failed. ", e);
        }
    }

    public ResticExecResult<Stats, Void> stats(
            String backupStoragePath,
            String backupPassword) throws SyncDuoException {
        FilesystemUtil.isFolderPathValid(backupStoragePath);
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("stats");
        commandLine.addArgument("--mode");
        commandLine.addArgument("blobs-per-file");
        ResticParser<Stats, Void> resticParser = new ResticParser<>(
                backupPassword,
                backupStoragePath,
                commandLine
        );
        CompletableFuture<ResticExecResult<Stats, Void>> future = resticParser.execute(
                null,
                stdout -> JsonUtil.parseResticJsonDocument(stdout, Stats.class),
                null,
                null,
                null
        );
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SyncDuoException("stats failed. future get failed. ", e);
        }
    }

    public ResticExecResult<CatConfig, Void> catConfig(
            String backupStoragePath,
            String backupPassword) throws SyncDuoException {
        FilesystemUtil.isFolderPathValid(backupStoragePath);
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("cat");
        commandLine.addArgument("config");
        ResticParser<CatConfig, Void> resticParser = new ResticParser<>(
                backupPassword,
                backupStoragePath,
                commandLine
        );
        CompletableFuture<ResticExecResult<CatConfig, Void>> future = resticParser.execute(
                null,
                stdout -> JsonUtil.parseResticJsonDocument(stdout, CatConfig.class),
                null,
                null,
                null
        );
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SyncDuoException("catConfig failed. future get failed. ", e);
        }
    }

    public ResticExecResult<Node, Void> ls(
            String backupStoragePath,
            String backupPassword,
            String snapshotId,
            String pathString) throws SyncDuoException {
        FilesystemUtil.isFolderPathValid(backupStoragePath);
        if (StringUtils.isBlank(snapshotId)) {
            throw new SyncDuoException("ls failed. snapshotsId is null");
        }
        CommandLine commandLine = getDefaultCommandLine();
        commandLine.addArgument("ls");
        commandLine.addArgument(snapshotId);
        if (StringUtils.isNotBlank(pathString)) {
            commandLine.addArgument(pathString);
        }
        ResticParser<Node, Void> resticParser = new ResticParser<>(
                backupPassword,
                backupStoragePath,
                commandLine
        );
        CompletableFuture<ResticExecResult<Node, Void>> future = resticParser.execute(
                null,
                null,
                stdout -> JsonUtil.parseResticJsonLines(stdout, Node.getCondition(), Node.class),
                null,
                null
        );
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new SyncDuoException("ls failed. future get failed. ", e);
        }
    }

    private static CommandLine getDefaultCommandLine() {
        CommandLine commandLine = new CommandLine("restic");
        commandLine.addArgument("--json");
        return commandLine;
    }
}
