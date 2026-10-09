-- ============================================================
-- V5__admin_panel.sql | Smart Blood Emergency Network
-- Admin panel support:
-- 1. Verification can be rejected with a reason (hospitals, blood banks).
--    A rejected profile leaves the queue until the owner edits and resubmits.
-- 2. Suspensions carry a reason that the user sees when trying to sign in.
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE hospitals
  ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER is_verified,
  ADD COLUMN rejected_at      DATETIME     NULL AFTER rejection_reason;

ALTER TABLE blood_banks
  ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER is_verified,
  ADD COLUMN rejected_at      DATETIME     NULL AFTER rejection_reason;

ALTER TABLE users
  ADD COLUMN suspension_reason VARCHAR(500) NULL AFTER is_enabled;

CREATE INDEX idx_req_updated ON blood_requests (status, updated_at);
CREATE INDEX idx_audit_created ON audit_logs (created_at);
