package com.syncduo.server.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.syncduo.server.workflow.model.db.SnapshotMetaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

@Mapper
public interface SnapshotMetaMapper extends BaseMapper<SnapshotMetaEntity> {

    /**
     * 使用 JSON_TABLE 找出在给定JSON数组列表中，但是不在数据库中的ID
     * @param snapshotIdsJson 一个 snapshot 的数据
     * @return 在数据库的ID列表
     */
    @Select("SELECT jt.candidate_id " +
            "FROM JSON_TABLE(#{snapshotIdsJson}, '$[*]' COLUMNS (candidate_id VARCHAR(400) PATH '$')) AS jt " +
            "WHERE jt.candidate_id COLLATE utf8mb4_unicode_ci NOT IN ( " + // 添加COLLATE
            "   SELECT snapshot_id FROM snapshot_meta " +
            ")")
    Set<String> findMissingSnapshotIds(@Param("snapshotIdsJson") String snapshotIdsJson);
}
