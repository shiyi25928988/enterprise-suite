-- M8: external directory mapping (WeCom / DingTalk sync).
-- (external_source, external_id) identifies an org/user inside its source
-- directory; NULL for locally created rows. MySQL unique indexes ignore
-- rows where any indexed column is NULL, which is exactly what we want.
ALTER TABLE sys_user
    ADD COLUMN external_source VARCHAR(32) NULL,
    ADD COLUMN external_id VARCHAR(64) NULL,
    ADD UNIQUE INDEX uk_user_external (external_source, external_id);

ALTER TABLE sys_org
    ADD COLUMN external_source VARCHAR(32) NULL,
    ADD COLUMN external_id VARCHAR(64) NULL,
    ADD UNIQUE INDEX uk_org_external (external_source, external_id);
