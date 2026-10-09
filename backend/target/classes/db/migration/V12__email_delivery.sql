-- ============================================================
-- V12__email_delivery.sql | Smart Blood Emergency Network
-- Reliable email delivery:
-- 1. email_outbox: every email is written here in the same transaction as the
--    action that caused it, then delivered by a background dispatcher with
--    retries and backoff. Nothing is sent for an action that rolled back, and
--    every email has a status an administrator can inspect. Bodies of
--    sensitive emails (one-time codes) are wiped once delivery finishes.
-- 2. user_login_devices: browsers/networks a user has signed in from, so a
--    sign-in from a new one triggers a security alert email.
-- ============================================================
SET NAMES utf8mb4;

CREATE TABLE email_outbox (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NULL,
  recipient       VARCHAR(150) NOT NULL,
  category        VARCHAR(20)  NOT NULL,
  template        VARCHAR(40)  NOT NULL,
  priority        INT          NOT NULL DEFAULT 2,
  subject         VARCHAR(200) NOT NULL,
  html_body       MEDIUMTEXT   NOT NULL,
  text_body       MEDIUMTEXT   NOT NULL,
  is_sensitive    BOOLEAN      NOT NULL DEFAULT FALSE,
  status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  attempts        INT          NOT NULL DEFAULT 0,
  next_attempt_at DATETIME     NOT NULL,
  locked_at       DATETIME     NULL,
  last_error      VARCHAR(500) NULL,
  sent_at         DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_outbox_due (status, next_attempt_at, priority),
  KEY idx_outbox_sent (status, sent_at),
  KEY idx_outbox_user (user_id, created_at),
  KEY idx_outbox_recipient (recipient)
) ENGINE=InnoDB;

CREATE TABLE user_login_devices (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  user_id       BIGINT       NOT NULL,
  fingerprint   CHAR(64)     NOT NULL,
  user_agent    VARCHAR(255) NULL,
  ip_address    VARCHAR(45)  NULL,
  first_seen_at DATETIME     NOT NULL,
  last_seen_at  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_login_device (user_id, fingerprint),
  CONSTRAINT fk_login_device_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
