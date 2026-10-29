-- =====================================================================
-- V6: application registry (SAS client onboarding) + role → app grants
--
-- sys_app is the business-facing view of a client: display name, icon,
-- portal link, access policy. It is linked 1:1 to a row in
-- oauth2_registered_client (the protocol-level client). Rows are created
-- through the admin console, which keeps both sides in sync.
--
-- Access policy:
--   ALL   - any authenticated user may obtain tokens for the app
--   ROLES - only users holding at least one granted role (sys_app_role)
--
-- Note: sys_app.deleted is a soft delete; the matching protocol client row
-- is hard-deleted, so a deleted app can never complete an authorize flow.
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_app (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_code VARCHAR(64) NOT NULL COMMENT 'stable business code, e.g. mes-web',
  app_name VARCHAR(128) NOT NULL,
  registered_client_id VARCHAR(100) NULL COMMENT 'oauth2_registered_client.id',
  client_id VARCHAR(100) NOT NULL,
  description VARCHAR(500) NULL,
  icon VARCHAR(32) NULL COMMENT 'emoji or 1-2 chars shown in the portal',
  home_url VARCHAR(500) NULL COMMENT 'portal entry link',
  client_type VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC (SPA + PKCE) | CONFIDENTIAL (server side)',
  redirect_uris VARCHAR(1000) NULL COMMENT 'comma separated',
  post_logout_redirect_uris VARCHAR(500) NULL COMMENT 'comma separated',
  grant_types VARCHAR(200) NOT NULL DEFAULT 'authorization_code,refresh_token',
  scopes VARCHAR(500) NOT NULL DEFAULT 'openid,profile',
  access_policy VARCHAR(16) NOT NULL DEFAULT 'ROLES' COMMENT 'ALL | ROLES',
  sort_no INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled 0 disabled',
  deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_app_code (app_code),
  UNIQUE KEY uk_app_client_id (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='registered application';

CREATE TABLE IF NOT EXISTS sys_app_role (
  app_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (app_id, role_id),
  KEY idx_app_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='roles allowed to access an application';
