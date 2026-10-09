-- ============================================================
-- V4__token_revocation_and_declines.sql | Smart Blood Emergency Network
-- 1. users.tokens_valid_after: access tokens issued before this instant are
--    rejected (logout, password reset, account suspension).
-- 2. request_declines: one row per donor who declined a request, so a decline
--    is recorded once and the request leaves that donor's feed.
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE users
  ADD COLUMN tokens_valid_after DATETIME(3) NULL AFTER is_enabled;

CREATE TABLE request_declines (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  request_id BIGINT   NOT NULL,
  donor_id   BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_decline_request_donor (request_id, donor_id),
  CONSTRAINT fk_decline_request FOREIGN KEY (request_id) REFERENCES blood_requests(id),
  CONSTRAINT fk_decline_donor   FOREIGN KEY (donor_id)   REFERENCES donors(id)
) ENGINE=InnoDB;
