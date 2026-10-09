-- ============================================================
-- V7__blood_bank_registration.sql | Smart Blood Emergency Network
-- Blood bank registration + license verification:
-- 1. Contact details (official email, alternate phone, website) and full
--    address (state, PIN code) used by the registration form.
-- 2. License certificate: stored filename (private upload dir, never served
--    statically), original name/size/type for display, issue/expiry dates.
-- 3. Optional logo (stored the same way).
-- 4. Who verified the license and when.
-- Additive only: existing rows keep their current values (all new columns NULL).
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE blood_banks
  ADD COLUMN contact_email          VARCHAR(150) NULL AFTER bank_name,
  ADD COLUMN alternate_phone        VARCHAR(15)  NULL AFTER phone,
  ADD COLUMN website                VARCHAR(255) NULL AFTER alternate_phone,
  ADD COLUMN state                  VARCHAR(50)  NULL AFTER city,
  ADD COLUMN pincode                VARCHAR(10)  NULL AFTER state,
  ADD COLUMN logo_file              VARCHAR(255) NULL AFTER website,
  ADD COLUMN license_issue_date     DATE         NULL AFTER license_number,
  ADD COLUMN license_expiry_date    DATE         NULL AFTER license_issue_date,
  ADD COLUMN license_document       VARCHAR(255) NULL AFTER license_expiry_date,
  ADD COLUMN license_document_name  VARCHAR(255) NULL AFTER license_document,
  ADD COLUMN license_document_type  VARCHAR(50)  NULL AFTER license_document_name,
  ADD COLUMN license_document_size  BIGINT       NULL AFTER license_document_type,
  ADD COLUMN license_submitted_at   DATETIME     NULL AFTER license_document_size,
  ADD COLUMN verified_at            DATETIME     NULL AFTER is_verified,
  ADD COLUMN verified_by            BIGINT       NULL AFTER verified_at;
