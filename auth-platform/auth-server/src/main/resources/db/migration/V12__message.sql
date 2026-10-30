-- =====================================================================
-- V12: in-app message center (M11)
--
-- sys_message is the third NotifyChannel ("inbox") landing place: the
-- same events that fan out to webhook / email contacts are rendered into
-- per-user rows here by InboxChannel, plus admin announcements.
--
-- Recipient is the USERNAME (not the id) on purpose: Flowable assignees
-- and every event payload already speak usernames, so the channel never
-- needs an id lookup to route a message.
--
-- uk_msg_event (event, ref_id, recipient) makes event deliveries idempotent
-- — the SLA scanner rescans every 30s and a re-render must not duplicate.
-- Both columns are NULL for announcements, and MySQL treats NULLs as
-- distinct in unique indexes, so announcements can repeat freely.
-- =====================================================================

CREATE TABLE IF NOT EXISTS sys_message (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  recipient    VARCHAR(64) NOT NULL COMMENT 'username of the receiver',
  type         VARCHAR(16) NOT NULL DEFAULT 'event' COMMENT 'event | announcement',
  event        VARCHAR(64) NULL COMMENT 'machine event name, NULL for announcements',
  title        VARCHAR(200) NOT NULL,
  body         VARCHAR(1000) NULL,
  ref_type     VARCHAR(32) NULL COMMENT 'task | user | sync ...',
  ref_id       VARCHAR(64) NULL COMMENT 'taskId / username / source ...',
  link         VARCHAR(300) NULL COMMENT 'SPA route to open on click',
  sender       VARCHAR(64) NULL COMMENT 'username of the announcer',
  read_at      DATETIME NULL,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_msg_event (event, ref_id, recipient),
  KEY idx_msg_recipient (recipient, id),
  KEY idx_msg_unread (recipient, read_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='in-app message center';
