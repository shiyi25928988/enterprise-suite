-- =====================================================================
-- V8: management operation audit log
--
-- Every write request against the admin API (/api/v1/**) and self-service
-- password change (/api/auth/password) is recorded by
-- OperationAuditFilter (method + path + operator + result status).
-- Request bodies are deliberately NOT stored — they can contain secrets
-- (passwords, client secrets); the URI template is enough to reconstruct
-- what happened, and sys_login_log already covers authentication.
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator VARCHAR(64) NOT NULL COMMENT 'username of the authenticated caller',
  method VARCHAR(10) NOT NULL COMMENT 'POST / PUT / PATCH / DELETE',
  uri VARCHAR(500) NOT NULL COMMENT 'actual request URI',
  action VARCHAR(64) NOT NULL COMMENT 'semantic action, e.g. USER_STATUS',
  status_code INT NOT NULL COMMENT 'HTTP status returned to the caller',
  ip VARCHAR(64) NULL COMMENT 'client IP (X-Forwarded-For aware)',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_oplog_time (create_time),
  KEY idx_oplog_operator (operator, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='management operation audit';
