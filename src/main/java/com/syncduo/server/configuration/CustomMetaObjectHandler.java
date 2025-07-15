package com.syncduo.server.configuration;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.syncduo.server.enums.DeletedEnum;
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
        this.strictInsertFill(metaObject, "recordDeleted", Integer.class, DeletedEnum.NOT_DELETED.getCode());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "lastUpdatedUser", String.class, "System");
        this.strictUpdateFill(metaObject, "lastUpdatedTime", Timestamp.class, this.getUTCTimestamp());
    }

    @Override
    public MetaObjectHandler strictFillStrategy(MetaObject metaObject, String fieldName, Supplier<?> fieldVal) {
        Object obj = fieldVal.get();
        if (Objects.nonNull(obj)) {
            metaObject.setValue(fieldName, obj);
        }
        return this;
    }

    private Timestamp getUTCTimestamp() {
        Instant now = Instant.now();
        return Timestamp.from(now);
    }
}
