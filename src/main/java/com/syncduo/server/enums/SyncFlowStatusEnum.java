package com.syncduo.server.enums;

public enum SyncFlowStatusEnum {

    NOT_SYNC,

    SYNC,

    PAUSE, // PAUSE 针对的是上游到下游的同步. 上游和下游跟文件系统的刷新不受影响

    RESUME, // RESUME DOES NOT STORE IN DB, BUT CONVERT TO RESUME OPERATION

    RESCAN, // RESCAN DOES NOT STORE IN DB, BUT CONVERT TO RESCAN OPERATION
}
