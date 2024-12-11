package com.syncduo.server.configuration;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.syncduo.server.enums.DeletedEnum;
import com.syncduo.server.enums.FileDesyncEnum;
import com.syncduo.server.enums.SyncFlowStatusEnum;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class CustomMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // base entity autofill
        this.strictInsertFill(metaObject, "createdUser", String.class, "System");
        this.strictInsertFill(metaObject, "createdTime", Timestamp.class, this.getUTCTimestamp());
        this.strictInsertFill(metaObject, "lastUpdatedUser", String.class, "System");
        this.strictInsertFill(metaObject, "lastUpdatedTime", Timestamp.class, this.getUTCTimestamp());

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
        this.strictUpdateFill(metaObject, "lastUpdatedUser", String.class, "System");
        this.strictUpdateFill(metaObject, "lastUpdatedTime", Timestamp.class, this.getUTCTimestamp());
        System.out.println(1);
    }

    @Override
    public MetaObjectHandler strictFillStrategy(MetaObject metaObject, String fieldName, Supplier<?> fieldVal) {
        Object obj = fieldVal.get();
        if (Objects.nonNull(obj)){
            metaObject.setValue(fieldName, obj);
        }
        return this;
    }

    private Timestamp getUTCTimestamp() {
        Instant now = Instant.now();
        return Timestamp.from(now);
    }
}
