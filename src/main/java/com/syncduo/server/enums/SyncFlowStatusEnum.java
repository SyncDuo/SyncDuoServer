package com.syncduo.server.enums;

public enum SyncFlowStatusEnum {

    NOT_SYNC,

    SYNC,

    PAUSE, // pause 针对的是上游到下游的同步. 上游和下游跟文件系统的刷新不受影响
}
