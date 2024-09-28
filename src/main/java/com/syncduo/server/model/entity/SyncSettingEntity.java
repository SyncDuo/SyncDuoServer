package com.syncduo.server.model.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("sync_setting")
public class SyncSettingEntity extends BaseEntity {

    @TableId
    private Long syncSettingId;

    private Long sourceFolderId;

    private Long destFolderId;

    private String filterCriteria;

    private Long flattenFolder;
}
