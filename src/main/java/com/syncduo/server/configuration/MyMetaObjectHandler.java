package com.syncduo.server.configuration;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

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

        // file entity autofill
        this.strictInsertFill(metaObject, "fileDeleted", Integer.class, 0);
        this.strictInsertFill(metaObject, "fileDesync", Integer.class, 0);

        // file operation entity autofill
        this.strictInsertFill(metaObject, "executeCount", Integer.class, 0);

        // sync flow entity autofill
        this.strictInsertFill(metaObject, "syncStatus", String.class, SyncFlowStatusEnum.NOT_SYNC.name());

        // sync setting entity autofill
        this.strictInsertFill(metaObject, "filterCriteria", String.class, "*");
        this.strictInsertFill(metaObject, "flattenFolder", Integer.class, 1);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "lastUpdatedUser", String.class, "System");
        this.strictInsertFill(
                metaObject, "lastUpdatedTime", LocalDateTime.class, LocalDateTime.now(CHINA_ZONE_ID));
    }
}
