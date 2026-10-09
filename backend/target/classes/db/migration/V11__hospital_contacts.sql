-- ============================================================
-- V11__hospital_contacts.sql | Smart Blood Emergency Network
-- Hospital contacts used when coordinating blood:
-- 1. A 24x7 emergency / blood desk number (the main phone may be a front desk).
-- 2. Official email for the hospital.
-- 3. The person responsible for blood requests (for example the blood
--    transfusion officer) and their designation.
-- Additive only: existing hospitals keep their current values (new columns NULL).
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE hospitals
  ADD COLUMN emergency_phone            VARCHAR(15)  NULL AFTER phone,
  ADD COLUMN official_email             VARCHAR(150) NULL AFTER emergency_phone,
  ADD COLUMN contact_person_name        VARCHAR(100) NULL AFTER official_email,
  ADD COLUMN contact_person_designation VARCHAR(100) NULL AFTER contact_person_name;
