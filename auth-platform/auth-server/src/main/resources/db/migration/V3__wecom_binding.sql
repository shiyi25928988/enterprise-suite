-- M5: unique index for WeCom scan-login binding.
-- (The wecom_userid column itself already exists since V2; this index makes
-- the binding unique once set — NULLs are allowed to repeat in MySQL.)

ALTER TABLE sys_user
  ADD UNIQUE INDEX uk_user_wecom_userid (wecom_userid);
