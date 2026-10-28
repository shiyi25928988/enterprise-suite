-- =====================================================================
-- V2: platform business schema (see development doc chapter 6.2)
-- =====================================================================

-- Organization (company → plant → workshop → line, materialized path for data scope)
CREATE TABLE sys_org (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id     BIGINT NOT NULL DEFAULT 0,
  org_code      VARCHAR(64)  NOT NULL COMMENT 'organization code, aligned with HR system',
  org_name      VARCHAR(128) NOT NULL,
  org_type      TINYINT NOT NULL DEFAULT 1 COMMENT '1 company 2 plant 3 workshop 4 line 5 department',
  org_path      VARCHAR(512) NOT NULL DEFAULT '/' COMMENT 'materialized path /1/5/12/ for data scope',
  sort_no       INT NOT NULL DEFAULT 0,
  leader_id     BIGINT NULL,
  status        TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled 0 disabled',
  deleted       TINYINT NOT NULL DEFAULT 0,
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_org_code (org_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='organization';

-- User
CREATE TABLE sys_user (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  username       VARCHAR(64)  NOT NULL COMMENT 'login name / employee number',
  password       VARCHAR(100) NOT NULL COMMENT 'DelegatingPasswordEncoder format, e.g. {bcrypt}...',
  real_name      VARCHAR(64)  NOT NULL,
  employee_no    VARCHAR(64)  NULL,
  mobile         VARCHAR(32)  NULL,
  email          VARCHAR(128) NULL,
  org_id         BIGINT NULL,
  position_id    BIGINT NULL,
  ldap_dn        VARCHAR(255) NULL,
  wecom_userid   VARCHAR(128) NULL,
  source         TINYINT NOT NULL DEFAULT 1 COMMENT '1 local 2 LDAP 3 WeCom 4 DingTalk',
  status         TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled 0 disabled 2 locked',
  password_update_time DATETIME NULL,
  last_login_time DATETIME NULL,
  fail_count     INT NOT NULL DEFAULT 0,
  deleted        TINYINT NOT NULL DEFAULT 0,
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username),
  KEY idx_org (org_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user';

CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  data_scope TINYINT NOT NULL DEFAULT 1 COMMENT '1 all 2 plant 3 workshop 4 department 5 self',
  status TINYINT NOT NULL DEFAULT 1,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role';

CREATE TABLE sys_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  perm_code VARCHAR(128) NOT NULL COMMENT 'e.g. mes:workorder:export',
  perm_name VARCHAR(64) NOT NULL,
  perm_type TINYINT NOT NULL COMMENT '1 menu 2 button 3 api',
  UNIQUE KEY uk_perm_code (perm_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='permission point';

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL, role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB;

CREATE TABLE sys_role_permission (
  role_id BIGINT NOT NULL, perm_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, perm_id)
) ENGINE=InnoDB;

-- Login audit
CREATE TABLE sys_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(64)  NULL,
  user_id     BIGINT NULL,
  login_type  VARCHAR(32)  NOT NULL COMMENT 'PASSWORD/SMS/WECOM/QRCODE',
  success     TINYINT NOT NULL,
  fail_reason VARCHAR(128) NULL,
  client_id   VARCHAR(128) NULL,
  ip          VARCHAR(64)  NULL,
  user_agent  VARCHAR(512) NULL,
  location    VARCHAR(128) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (user_id, create_time),
  KEY idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='login log';

-- Persistent JWK signing keys (kid history for rotation; private key material
-- must live in KMS/Vault in production — see development doc 7.3)
CREATE TABLE auth_jwk (
  kid        VARCHAR(64) NOT NULL,
  key_json   TEXT NOT NULL COMMENT 'full RSA JWK JSON incl. private key',
  status     TINYINT NOT NULL DEFAULT 1 COMMENT '1 active 2 retired',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retired_at DATETIME NULL,
  PRIMARY KEY (kid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='signing keys';

-- ---------------------------------------------------------------------
-- Seed data (skeleton stage only — passwords MUST be rotated on first login)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_org (id, parent_id, org_code, org_name, org_type, org_path)
VALUES (1, 0, 'HQ', 'Headquarters', 1, '/1/'),
       (2, 1, 'P1',  'Plant No.1',   2, '/1/2/');

INSERT IGNORE INTO sys_user (id, username, password, real_name, org_id)
VALUES (1, 'admin',    '{noop}admin123', 'Administrator', 1),
       (2, 'zhangsan', '{noop}Zhang@123','Zhang San',     2);

INSERT IGNORE INTO sys_role (id, role_code, role_name) VALUES (1, 'ADMIN', 'Administrator');

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1);
