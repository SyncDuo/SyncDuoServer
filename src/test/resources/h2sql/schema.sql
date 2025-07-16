CREATE TABLE sync_setting (
-- define columns (name / type / default value / nullable)
                        sync_setting_id bigint auto_increment,
                        created_user varchar(255),
                        created_time timestamp,
                        last_updated_user varchar(255),
                        last_updated_time varchar(255),
                        record_deleted int,
                        sync_flow_id bigint,
                        filter_criteria text,
-- select one of the defined columns as the Primary Key
                        CONSTRAINT sync_setting_pk PRIMARY KEY (sync_setting_id)
);

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
-- select one of the defined columns as the Primary Key
                              CONSTRAINT sync_flow_pk PRIMARY KEY (sync_flow_id)
);

CREATE TABLE system_config (
-- define columns (name / type / default value / nullable)
                           system_config_id int unsigned auto_increment,
                           created_user varchar(255),
                           created_time timestamp,
                           last_updated_user varchar(255),
                           last_updated_time varchar(255),
                           record_deleted int,
                           backup_storage_path varchar(4095),
-- select one of the defined columns as the Primary Key
                           CONSTRAINT system_config_pk PRIMARY KEY (system_config_id)
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
                              CONSTRAINT copy_job_log_pk PRIMARY KEY (copy_job_id)
);