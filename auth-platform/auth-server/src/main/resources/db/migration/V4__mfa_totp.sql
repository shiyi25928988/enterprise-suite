-- M5b: TOTP second factor (per-user opt-in; admins should enable it).
-- Secret is a Base32 string (RFC 4648, no padding), 160-bit random.

ALTER TABLE sys_user
  ADD COLUMN mfa_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '1 = TOTP code required at login',
  ADD COLUMN totp_secret VARCHAR(64) NULL COMMENT 'Base32 TOTP shared secret';
