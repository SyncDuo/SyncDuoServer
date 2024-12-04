package com.syncduo.server.configuration;

import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MetaObjectHandler implements com.baomidou.mybatisplus.core.handlers.MetaObjectHandler {

    private static final ZoneId CHINA_ZONE_ID = ZoneId.of("Asia/Shanghai");

    @Override
    public void insertFill(MetaObject metaObject) {
        // base entity autofill
        this.strictInsertFill(metaObject, "createdUser", String.class, "System");
        this.strictInsertFill(
                metaObject, "createdTime", LocalDateTime.class, LocalDateTime.now(CHINA_ZONE_ID));
        this.strictInsertFill(metaObject, "lastUpdatedUser", String.class, "System");
        this.strictInsertFill(
                metaObject, "lastUpdatedTime", LocalDateTime.class, LocalDateTime.now(CHINA_ZONE_ID));

        // folder entity autofill
        this.strictInsertFill(
                metaObject, "folderDeleted", Integer.class, DeletedEnum.NOT_DELETED.getCode());

        // file entity autofill
        this.strictInsertFill(
                metaObject, "fileDeleted", Integer.class, DeletedEnum.NOT_DELETED.getCode());
        this.strictInsertFill(metaObject, "fileDesync", Integer.class, FileDesyncEnum.FILE_SYNC.getCode());

        // file operation entity autofill
        this.strictInsertFill(metaObject, "executeCount", Integer.class, 0);

        // sync flow entity autofill
        this.strictInsertFill(metaObject, "syncStatus", String.class, SyncFlowStatusEnum.NOT_SYNC.name());
        this.strictInsertFill(
                metaObject, "syncFlowDeleted", Integer.class, DeletedEnum.NOT_DELETED.getCode());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "lastUpdatedUser", String.class, "System");
        this.strictInsertFill(
                metaObject, "lastUpdatedTime", LocalDateTime.class, LocalDateTime.now(CHINA_ZONE_ID));
    }
}
