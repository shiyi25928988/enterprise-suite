-- M5c: DingTalk scan-login binding + badge (工牌) code login.
-- dingtalk_userid mirrors wecom_userid (V2/V3): unique once set, NULLs repeat.
-- badge_code is a per-user printable secret shown as a QR on the badge;
-- scanning it on any terminal logs the holder in without a password.
ALTER TABLE sys_user
  ADD COLUMN dingtalk_userid VARCHAR(64) NULL,
  ADD COLUMN badge_code VARCHAR(64) NULL;

ALTER TABLE sys_user
  ADD UNIQUE INDEX uk_user_dingtalk_userid (dingtalk_userid),
  ADD UNIQUE INDEX uk_user_badge_code (badge_code);

-- V6b: per-device token policies — token lifetimes are per client, not
-- global. NULL keeps the platform defaults (30 min access / 10 min device
-- code); device-flow clients (workshop screens) typically raise these.
ALTER TABLE sys_app
  ADD COLUMN access_token_ttl_minutes INT NULL,
  ADD COLUMN device_code_ttl_minutes INT NULL;
