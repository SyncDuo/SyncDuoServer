package com.syncduo.server.model.api.snapshots;

import com.syncduo.server.enums.ResticNodeTypeEnum;
import com.syncduo.server.model.restic.ls.Node;
import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.text.DecimalFormat;

@Data
public class SnapshotFileInfo {

    private String snapshotId;

    private String path;

    private String fileName;

    private String lastModifiedTime;

    private String size;

    private String type;

    private SnapshotFileInfo() {}

    public static SnapshotFileInfo getFromResticNode(String snapshotId, Node node) {
        SnapshotFileInfo result = new SnapshotFileInfo();
        result.setSnapshotId(snapshotId);
        if (ObjectUtils.isEmpty(node)) {
            return result;
        }
        result.setPath(node.getPath());
        result.setFileName(node.getName());
        result.setLastModifiedTime(Timestamp.from(node.getMtime()).toString());
        if (ObjectUtils.isNotEmpty(node.getSize())) {
            // BigInteger 格式化使用的变量, bytes -> MB
            BigDecimal mbDivider = new BigDecimal(1024 * 1024);
            DecimalFormat decimalFormat = new DecimalFormat("0.00");
            BigDecimal backupMb = new BigDecimal(node.getSize()).divide(mbDivider, 2, RoundingMode.HALF_UP);
            result.setSize(decimalFormat.format(backupMb));
        }
        result.setType(ResticNodeTypeEnum.fromString(node.getType()).getType());
        return result;
    }
}
