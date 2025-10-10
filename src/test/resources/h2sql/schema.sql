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