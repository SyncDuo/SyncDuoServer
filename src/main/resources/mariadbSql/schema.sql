-- 创建 sync_flow 表
drop table if exists sync_flow;
CREATE TABLE sync_flow (
                           sync_flow_id BIGINT NOT NULL AUTO_INCREMENT,
                           created_user VARCHAR(255) NOT NULL,
                           created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           last_updated_user VARCHAR(255) NOT NULL,
                           last_updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                           record_deleted INT NULL DEFAULT 0,
                           source_folder_path VARCHAR(4095) NULL,
                           dest_folder_path VARCHAR(4095) NULL,
                           sync_flow_name VARCHAR(255) NULL,
                           sync_status VARCHAR(255) NULL,
                           last_sync_time TIMESTAMP NULL,
                           filter_criteria TEXT NULL,
    -- 主键
                           PRIMARY KEY (sync_flow_id),
    -- 索引
                           INDEX idx_sync_flow_name (sync_flow_name),
                           INDEX idx_last_sync_time (last_sync_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 copy_job 表
drop table if exists copy_job;
CREATE TABLE copy_job (
                          copy_job_id BIGINT NOT NULL AUTO_INCREMENT,
                          created_user VARCHAR(255) NOT NULL,
                          created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          last_updated_user VARCHAR(255) NOT NULL,
                          last_updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                          record_deleted INT NULL DEFAULT 0,
                          error_message TEXT NULL,
                          started_at TIMESTAMP NULL,
                          finished_at TIMESTAMP NULL,
                          copy_job_status VARCHAR(255) NULL,
                          transferred_files BIGINT NULL DEFAULT 0,
                          transferred_bytes BIGINT NULL DEFAULT 0,
                          rclone_job_id INT NULL,
                          sync_flow_id BIGINT NULL,
    -- 主键
                          PRIMARY KEY (copy_job_id),
    -- 索引
                          INDEX idx_started_at (started_at),
                          INDEX idx_finished_at (finished_at),
                          INDEX idx_sync_flow_id (sync_flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 backup_job 表
drop table if exists backup_job;
CREATE TABLE backup_job (
                            backup_job_id BIGINT NOT NULL AUTO_INCREMENT,
                            created_user VARCHAR(255) NOT NULL,
                            created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            last_updated_user VARCHAR(255) NOT NULL,
                            last_updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            record_deleted INT NULL DEFAULT 0,
                            error_message TEXT NULL,
                            success_message TEXT NULL,
                            started_at TIMESTAMP NULL,
                            finished_at TIMESTAMP NULL,
                            backup_job_status VARCHAR(255) NULL,
                            backup_files BIGINT NULL DEFAULT 0,
                            backup_bytes BIGINT NULL DEFAULT 0,
                            snapshot_id TEXT NULL,
                            sync_flow_id BIGINT NULL,
    -- 主键
                            PRIMARY KEY (backup_job_id),
    -- 索引
                            INDEX idx_started_at (started_at),
                            INDEX idx_finished_at (finished_at),
                            INDEX idx_sync_flow_id (sync_flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建 restore_job 表
drop table if exists restore_job;
CREATE TABLE restore_job (
                             restore_job_id BIGINT NOT NULL AUTO_INCREMENT,
                             created_user VARCHAR(255) NOT NULL,
                             created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             last_updated_user VARCHAR(255) NOT NULL,
                             last_updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             record_deleted INT NULL DEFAULT 0,
                             restore_job_status VARCHAR(255) NULL,
                             error_message TEXT NULL,
                             seconds_elapsed BIGINT NULL DEFAULT 0,
                             restore_root_path TEXT NULL,
                             restore_files BIGINT NULL DEFAULT 0,
                             restore_bytes BIGINT NULL DEFAULT 0,
                             snapshot_id TEXT NULL,
    -- 主键
                             PRIMARY KEY (restore_job_id),
    -- 索引
                             INDEX idx_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;