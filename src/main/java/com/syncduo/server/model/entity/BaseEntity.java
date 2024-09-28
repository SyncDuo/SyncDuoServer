package com.syncduo.server.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
public abstract class BaseEntity {

    private String createdUser;

    private Timestamp createdTime;

    private String lastUpdatedUser;

    private Timestamp lastUpdatedTime;
}
