-- =====================================================================
-- V7: account security hardening
--   must_change_password — accounts created by an admin / bulk import /
--   password reset carry an initial secret every colleague can guess, so
--   the holder has to replace it before the platform lets them in.
-- =====================================================================

ALTER TABLE sys_user
  ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0
    COMMENT '1 = the holder must set a new password at next login' AFTER fail_count;

-- online-session listing resolves candidates by recent successful login
CREATE INDEX idx_last_login ON sys_user (last_login_time);
