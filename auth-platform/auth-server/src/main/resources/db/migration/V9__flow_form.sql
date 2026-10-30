-- W1: workflow form schemas — one row per process definition key, rendered
-- dynamically by the SPA when a user starts an instance.
CREATE TABLE sys_flow_form (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    process_key VARCHAR(64) NOT NULL,
    form_name VARCHAR(64) NOT NULL,
    schema_json MEDIUMTEXT NOT NULL,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flow_form_key (process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
