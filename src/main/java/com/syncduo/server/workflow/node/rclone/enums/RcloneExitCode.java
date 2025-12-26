package com.syncduo.server.workflow.node.rclone.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * rclone 命令退出码枚举
 * 官方定义：<a href="https://rclone.org/docs/#exit-code">...</a>
 */
@Getter
@AllArgsConstructor
public enum RcloneExitCode {
    /**
     * 0 - 成功
     */
    SUCCESS(0, "Success"),
    /**
     * 1 - 未分类错误
     */
    UNCATEGORIZED_ERROR(1, "Error not otherwise categorised"),
    /**
     * 2 - 语法或用法错误
     */
    SYNTAX_USAGE_ERROR(2, "Syntax or usage error"),
    /**
     * 3 - 目录未找到
     */
    DIR_NOT_FOUND(3, "Directory not found"),
    /**
     * 4 - 文件未找到
     */
    FILE_NOT_FOUND(4, "File not found"),
    /**
     * 5 - 可重试的临时性错误
     */
    RETRY_ERROR(5, "Temporary error (one that more retries might fix) (Retry errors)"),
    /**
     * 6 - 无需重试的轻微错误
     */
    NO_RETRY_ERROR(6, "Less serious errors (like 461 errors from dropbox) (NoRetry errors)"),
    /**
     * 7 - 致命错误（重试无法解决）
     */
    FATAL_ERROR(7, "Fatal error (one that more retries won't fix, like account suspended) (Fatal errors)"),
    /**
     * 8 - 传输量超限（由 --max-transfer 触发）
     */
    TRANSFER_EXCEEDED(8, "Transfer exceeded - limit set by --max-transfer reached"),
    /**
     * 9 - 操作成功但无文件传输
     */
    NO_FILES_TRANSFERRED(9, "Operation successful, but no files transferred"),
    /**
     * 10 - 运行时间超限（由 --max-duration 触发）
     */
    DURATION_EXCEEDED(10, "Duration exceeded - limit set by --max-duration reached"),

    UNKNOWN(-1, "UNKNOWN EXITCODE"),

    ERROR_WITHOUT_EXITCODE(-2, "命令没有返回 exitcode"),

    ;

    /**
     * 退出码的数值
     */
    private final int code;
    /**
     * 描述信息
     */
    private final String description;

    /**
     * 内部映射，用于通过 code 快速查找枚举
     */
    private static final Map<Integer, RcloneExitCode> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(RcloneExitCode::getCode, Function.identity()));

    /**
     * 根据退出码数值获取对应的枚举实例
     *
     * @param code 退出码
     * @return 对应的枚举实例，如果未找到则返回 null
     */
    public static RcloneExitCode fromCode(int code) {
        return CODE_MAP.get(code);
    }

    /**
     * 判断该退出码是否表示“成功”状态（包括无文件传输的情况）
     *
     * @return true 表示成功
     */
    public boolean isSuccess() {
        return this == SUCCESS || this == NO_FILES_TRANSFERRED;
    }
}
