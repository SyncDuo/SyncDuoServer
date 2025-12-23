package com.syncduo.server.workflow.core.enums;

import lombok.Getter;

@Getter
public enum ExecStatus {

    RUNNING,

    SUCCESS,

    FAILED,

    PENDING, // 只给 Flow 使用, 表示上一次执行已结束, 等待下一次执行
}
