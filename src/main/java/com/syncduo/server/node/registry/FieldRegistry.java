package com.syncduo.server.node.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.syncduo.server.core.model.execution.FlowContext;
import com.syncduo.server.node.rclone.model.CopyResult;
import com.syncduo.server.node.restic.model.Snapshot;
import com.syncduo.server.node.restic.model.SnapshotNode;
import com.syncduo.server.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class FieldRegistry {

    public static final String SOURCE_DIRECTORY = "SOURCE_DIRECTORY";

    public static final String DEDUPLICATE_FILES = "DEDUPLICATE_FILES";

    public static final String RESTIC_BACKUP_REPOSITORY = "RESTIC_BACKUP_REPOSITORY";

    public static final String RESTIC_PASSWORD = "RESTIC_PASSWORD";

    public static final String RESTIC_BACKUP_RESULT = "RESTIC_BACKUP_RESULT";

    public static final String RESTIC_SNAPSHOTS = "RESTIC_SNAPSHOTS";

    public static final String RESTIC_SNAPSHOT_ID = "RESTIC_SNAPSHOT_ID";

    public static final String RESTIC_LS_FILTER = "RESTIC_LS_FILTER";

    public static final String RESTIC_SNAPSHOT_NODES = "RESTIC_SNAPSHOT_NODES";

    public static final String RESTIC_RESTORE_RESULT = "RESTIC_RESTORE_RESULT";

    public static final String DST_DIRECTORY = "DST_DIRECTORY";

    public static final String RCLONE_COPY_RESULT = "RCLONE_COPY_RESULT";

    // 静态元数据映射表
    private static final Map<String, Definition> FieldDefinitionMap = new HashMap<>();

    static {
        // 初始化元数据映射关系
        FieldDefinitionMap.put(SOURCE_DIRECTORY, new Definition(
                new TypeReference<String>() {
                },
                "源目录路径",
                "filesystem"
        ));
        FieldDefinitionMap.put(DST_DIRECTORY, new Definition(
                new TypeReference<String>() {
                },
                "目的目录路径",
                "filesystem"
        ));
        FieldDefinitionMap.put(DEDUPLICATE_FILES, new Definition(
                new TypeReference<List<String>>() {
                },
                "去重的文件集合",
                "filesystem"
        ));
        FieldDefinitionMap.put(RESTIC_BACKUP_REPOSITORY, new Definition(
                new TypeReference<String>() {
                },
                "restic 备份目录",
                "restic"
        ));
        FieldDefinitionMap.put(RESTIC_PASSWORD, new Definition(
                new TypeReference<String>() {
                },
                "restic 备份目录对应的密码",
                "restic"
        ));
        FieldDefinitionMap.put(RESTIC_BACKUP_RESULT, new Definition(
                new TypeReference<String>() {
                },
                "restic 备份结果(json lines)",
                "restic"
        ));
        FieldDefinitionMap.put(RESTIC_SNAPSHOTS, new Definition(
                new TypeReference<List<Snapshot>>() {
                },
                "restic snapshots 命令结果(json lines)",
                "restic"
        ));
        FieldDefinitionMap.put(RESTIC_SNAPSHOT_ID, new Definition(
                new TypeReference<String>() {
                },
                "restic snapshot id",
                "restic"
        ));
        FieldDefinitionMap.put(RESTIC_LS_FILTER, new Definition(
                new TypeReference<String>() {
                },
                "restic ls 命令传入的 filter, 过滤文件和文件夹路径",
                "restic"
        ));
        FieldDefinitionMap.put(RESTIC_SNAPSHOT_NODES, new Definition(
                new TypeReference<List<SnapshotNode>>() {
                },
                "restic ls 命令返回的 node 信息",
                "restic"
        ));
        FieldDefinitionMap.put(RESTIC_RESTORE_RESULT, new Definition(
                new TypeReference<Path>() {
                },
                "restic restore 的结果(单个文件则保持原样, 文件夹或多个文件/文件夹则 zip 文件",
                "restic"
        ));
        FieldDefinitionMap.put(RCLONE_COPY_RESULT, new Definition(
                new TypeReference<CopyResult>() {
                },
                "rclone使用copy命令返回的结果",
                "rclone"
        ));
    }

    public static String getString(String fieldName, FlowContext context) {
        return getValue(fieldName, context);
    }

    public static List<Snapshot> getResticSnapshots(FlowContext context) {
        return getValue(FieldRegistry.RESTIC_SNAPSHOTS, context);
    }

    /**
     * 获取转换后的值（使用字段对应的TypeReference）
     *
     * @param fieldName 字段名
     */
    @SuppressWarnings("unchecked")
    public static <T> T getValue(String fieldName, FlowContext context) {
        Definition definition = getMeta(fieldName);
        if (ObjectUtils.isEmpty(definition)) {
            throw new IllegalArgumentException("找不到 %s 的定义".formatted(fieldName));
        }
        //
        Object rawValue = context.get(fieldName);
        if (rawValue == null) {
            throw new IllegalStateException("在 context %s 中找不到 %s 的值".formatted(
                    fieldName,
                    context.getData()));
        }
        return (T) JsonUtil.convertValue(rawValue, definition.typeReference());
    }

    public static Definition getMeta(String name) {
        return FieldDefinitionMap.get(name);
    }

    public static Map<String, Definition> getAllField() {
        return Collections.unmodifiableMap(FieldDefinitionMap);
    }

    public record Definition(TypeReference<?> typeReference, String description, String group) {
    }
}
