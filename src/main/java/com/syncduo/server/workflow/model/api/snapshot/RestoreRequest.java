package com.syncduo.server.workflow.model.api.snapshot;

import com.syncduo.server.workflow.model.db.SnapshotMetaEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestoreRequest {
    private SnapshotMetaEntity snapshotMetaEntity;

    private List<SnapshotItemDTO> snapshotItemDTOList;
}
