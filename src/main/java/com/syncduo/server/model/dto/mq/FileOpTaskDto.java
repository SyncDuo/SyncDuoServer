package com.syncduo.server.model.dto.mq;

import com.syncduo.server.model.entity.SyncFlowEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileOpTaskDto {

    private SyncFlowEntity syncFlowEntity;
}
