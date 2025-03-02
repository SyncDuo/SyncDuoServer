package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public abstract class BaseEntity {

    @TableField(fill = FieldFill.INSERT)
    private String createdUser;

    @TableField(fill = FieldFill.INSERT)
    private Timestamp createdTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String lastUpdatedUser;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Timestamp lastUpdatedTime;

    @TableField(fill = FieldFill.INSERT)
    private Integer recordDeleted;
}
