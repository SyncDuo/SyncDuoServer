CREATE TABLE sync_flow (
-- define columns (name / type / default value / nullable)
                           sync_flow_id bigint auto_increment,
                           created_user varchar(255),
                           created_time timestamp,
                           last_updated_user varchar(255),
                           last_updated_time varchar(255),
                           record_deleted int,
                           source_folder_path varchar(4095),
                           dest_folder_path varchar(4095),
                           sync_flow_name varchar(255),
                           sync_status varchar(255),
                           last_sync_time timestamp,
                           filter_criteria text,
                           sync_flow_type varchar(255),
-- select one of the defined columns as the Primary Key
                           CONSTRAINT sync_flow_pk PRIMARY KEY (sync_flow_id)
);

CREATE TABLE copy_job (
-- define columns (name / type / default value / nullable)
                          copy_job_id bigint auto_increment,
                          created_user varchar(255),
                          created_time timestamp,
                          last_updated_user varchar(255),
                          last_updated_time varchar(255),
                          record_deleted int,
                          error_message text,
                          started_at timestamp,
                          finished_at timestamp,
                          copy_job_status varchar(255),
                          transferred_files bigint,
                          transferred_bytes bigint,
                          rclone_job_id int,
                          sync_flow_id bigint,
-- select one of the defined columns as the Primary Key
                          CONSTRAINT copy_job_pk PRIMARY KEY (copy_job_id)
);

CREATE TABLE backup_job (
-- define columns (name / type / default value / nullable)
                            backup_job_id bigint auto_increment,
                            created_user varchar(255),
                            created_time timestamp,
                            last_updated_user varchar(255),
                            last_updated_time varchar(255),
                            record_deleted int,
                            error_message text,
                            success_message text,
                            started_at timestamp,
                            finished_at timestamp,
                            backup_job_status varchar(255),
                            backup_files bigint,
                            backup_bytes bigint,
                            snapshot_id text,
                            sync_flow_id bigint,
-- select one of the defined columns as the Primary Key
                            CONSTRAINT backup_job_pk PRIMARY KEY (backup_job_id)
);

CREATE TABLE restore_job (
-- define columns (name / type / default value / nullable)
                             restore_job_id bigint auto_increment,
                             created_user varchar(255),
                             created_time timestamp,
                             last_updated_user varchar(255),
                             last_updated_time varchar(255),
                             record_deleted int,
                             restore_job_status varchar(255),
                             error_message text,
                             seconds_elapsed bigint,
                             origin_file_path text,
                             restore_root_path text,
                             restore_full_path text,
                             restore_files bigint,
                             restore_bytes bigint,
                             snapshot_id text,
-- select one of the defined columns as the Primary Key
                             CONSTRAINT restore_job_pk PRIMARY KEY (restore_job_id)
);

-- 流程定义表
DROP TABLE IF EXISTS flow_definition;
CREATE TABLE
    flow_definition (
                        flow_definition_id bigint(20) NOT NULL AUTO_INCREMENT,
                        created_user varchar(255) not null,
                        created_time TIMESTAMP not null DEFAULT CURRENT_TIMESTAMP(),
                        last_updated_user varchar(255) not null,
                        last_updated_time TIMESTAMP not null DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP(),
                        record_deleted int(11) DEFAULT 0,
                        name VARCHAR(100) NOT NULL,
                        description TEXT,
                        definition JSON NOT NULL, -- 完整的流程定义JSON
                        cron_config VARCHAR(255)  not null,
                        version INT DEFAULT 1,
                        enabled int default 1,
                        PRIMARY KEY (`flow_definition_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 流程执行记录表
DROP TABLE IF EXISTS flow_execution;
CREATE TABLE
    flow_execution (
                       flow_execution_id bigint(20) NOT NULL AUTO_INCREMENT,
                       created_user varchar(255) not null,
                       created_time TIMESTAMP not null DEFAULT CURRENT_TIMESTAMP(),
                       last_updated_user varchar(255) not null,
                       last_updated_time TIMESTAMP not null DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP(),
                       record_deleted int(11) DEFAULT 0,
                       flow_execution_uuid_v4 varchar(255) not null,
                       flow_definition_id bigint(20) NOT NULL,
                       execution_status VARCHAR(20) NOT NULL, -- pending, running, success, failed, cancelled
                       context_data JSON, -- 执行结果, 即 context.data
                       error_message TEXT, -- 错误信息
                       start_time TIMESTAMP, -- 开始时间
                       end_time TIMESTAMP, -- 结束时间
                       PRIMARY KEY (`flow_execution_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 节点执行日志表
DROP TABLE IF EXISTS node_execution;
CREATE TABLE
    node_execution (
                       node_execution_id BIGINT(20) AUTO_INCREMENT,
                       created_user varchar(255) not null,
                       created_time TIMESTAMP not null DEFAULT CURRENT_TIMESTAMP(),
                       last_updated_user varchar(255) not null,
                       last_updated_time TIMESTAMP not null DEFAULT CURRENT_TIMESTAMP() ON UPDATE CURRENT_TIMESTAMP(),
                       record_deleted int(11) DEFAULT 0,
                       node_execution_uuid_v4 varchar(255) not null,
                       flow_definition_id bigint(20) NOT NULL,
                       flow_execution_id bigint(20) NOT NULL,
                       node_id VARCHAR(100) NOT NULL, -- dag 内唯一
                       execution_status VARCHAR(20) NOT NULL, -- pending, running, success, failed, cancelled
                       input_data JSON NOT NULL, -- 输入参数
                       output_data JSON, -- 输出参数
                       node_execution_log TEXT, -- 执行日志
                       start_time TIMESTAMP,
                       end_time TIMESTAMP,
                       PRIMARY KEY (`node_execution_id`)
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;