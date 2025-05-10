CREATE TABLE file (
-- define columns (name / type / default value / nullable)
                        file_id bigint auto_increment,
                        created_user varchar(255),
                        created_time timestamp,
                        last_updated_user varchar(255),
                        last_updated_time varchar(255),
                        record_deleted int,
                        file_name      VARCHAR(255)  NOT NULL,
                        file_extension       VARCHAR(255)  NOT NULL,
                        file_md5_checksum  VARCHAR(255),
                        file_created_time timestamp,
                        file_last_modified_time timestamp,
                        file_unique_hash varchar(255),
                        folder_id long,
                        relative_path varchar(500),
-- select one of the defined columns as the Primary Key
                        CONSTRAINT file_pk PRIMARY KEY (file_id)
);

CREATE TABLE folder (
-- define columns (name / type / default value / nullable)
                      folder_id bigint auto_increment,
                      created_user varchar(255),
                      created_time timestamp,
                      last_updated_user varchar(255),
                      last_updated_time varchar(255),
                      record_deleted int,
                      folder_name varchar(255),
                      folder_full_path varchar(255),
-- select one of the defined columns as the Primary Key
                      CONSTRAINT folder_pk PRIMARY KEY (folder_id)
);

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
                        sync_mode int,
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
                              source_folder_id bigint,
                              dest_folder_id bigint,
                              sync_flow_type varchar(255),
                              sync_flow_name varchar(255),
                              sync_status varchar(255),
                              last_sync_time timestamp,
-- select one of the defined columns as the Primary Key
                              CONSTRAINT sync_flow_pk PRIMARY KEY (sync_flow_id)
);

CREATE TABLE system_config (
-- define columns (name / type / default value / nullable)
                           system_config_id bigint auto_increment,
                           created_user varchar(255),
                           created_time timestamp,
                           last_updated_user varchar(255),
                           last_updated_time varchar(255),
                           record_deleted int,
                           sync_storage_path varchar(255),
                           backup_storage_path varchar(255),
                           handler_min_threads int,
                           handler_max_threads int,
-- select one of the defined columns as the Primary Key
                           CONSTRAINT system_config_pk PRIMARY KEY (system_config_id)
);

CREATE TABLE file_sync_mapping (
-- define columns (name / type / default value / nullable)
                               file_sync_mapping_id bigint auto_increment,
                               created_user varchar(255),
                               created_time timestamp,
                               last_updated_user varchar(255),
                               last_updated_time varchar(255),
                               record_deleted int,
                               sync_flow_id bigint,
                               source_file_id bigint,
                               dest_file_id bigint,
                               file_desync int,
-- select one of the defined columns as the Primary Key
                               CONSTRAINT file_sync_mapping_pk PRIMARY KEY (file_sync_mapping_id)
);

CREATE TABLE file_event (
-- define columns (name / type / default value / nullable)
                                   file_event_id bigint auto_increment,
                                   created_user varchar(255),
                                   created_time timestamp,
                                   last_updated_user varchar(255),
                                   last_updated_time varchar(255),
                                   record_deleted int,
                                   file_event_type varchar(255),
                                   folder_id bigint,
                                   file_id bigint,
-- select one of the defined columns as the Primary Key
                                   CONSTRAINT file_event_pk PRIMARY KEY (file_event_id)
);