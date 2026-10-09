-- ============================================================
-- V8__donor_profile_details.sql | Smart Blood Emergency Network
-- Donor profile details:
-- 1. Date of birth is the source of age. The old `age` column stays for rows
--    saved before this migration and is cleared once a date of birth is saved.
-- 2. Full address (state, PIN code) alongside the existing city/coordinates.
-- 3. Donation history answer, contact preference and consent to receive
--    donation requests (NULL = not answered yet; FALSE = opted out of alerts
--    and nearby search, TRUE = opted in).
-- 4. Optional gender, alternate and emergency contacts, profile photo
--    (stored privately, like blood bank logos).
-- 5. Self-declared health screening answers. These are not an eligibility
--    decision: staff at the blood centre decide that. Only the donor can read them.
-- Additive only: existing rows keep their current values (new columns NULL).
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE donors
  ADD COLUMN date_of_birth              DATE         NULL AFTER blood_group,
  ADD COLUMN gender                     VARCHAR(20)  NULL AFTER date_of_birth,
  ADD COLUMN address                    VARCHAR(255) NULL AFTER age,
  ADD COLUMN state                      VARCHAR(50)  NULL AFTER city,
  ADD COLUMN pincode                    VARCHAR(6)   NULL AFTER state,
  ADD COLUMN has_donated_before         BOOLEAN      NULL AFTER longitude,
  ADD COLUMN preferred_contact_method   VARCHAR(20)  NULL AFTER availability,
  ADD COLUMN contact_consent            BOOLEAN      NULL AFTER preferred_contact_method,
  ADD COLUMN contact_consent_updated_at DATETIME     NULL AFTER contact_consent,
  ADD COLUMN alternate_phone            VARCHAR(15)  NULL AFTER contact_consent_updated_at,
  ADD COLUMN emergency_contact_name     VARCHAR(100) NULL AFTER alternate_phone,
  ADD COLUMN emergency_contact_phone    VARCHAR(15)  NULL AFTER emergency_contact_name,
  ADD COLUMN photo_file                 VARCHAR(255) NULL AFTER emergency_contact_phone,
  ADD COLUMN screen_feeling_well        VARCHAR(16)  NULL AFTER photo_file,
  ADD COLUMN screen_recent_illness      VARCHAR(16)  NULL AFTER screen_feeling_well,
  ADD COLUMN screen_current_medication  VARCHAR(16)  NULL AFTER screen_recent_illness,
  ADD COLUMN screen_recent_procedure    VARCHAR(16)  NULL AFTER screen_current_medication,
  ADD COLUMN screen_chronic_condition   VARCHAR(16)  NULL AFTER screen_recent_procedure,
  ADD COLUMN screen_pregnancy           VARCHAR(16)  NULL AFTER screen_chronic_condition,
  ADD COLUMN health_screened_at         DATETIME     NULL AFTER screen_pregnancy;

-- A recorded last donation date means the donor has donated before.
UPDATE donors SET has_donated_before = TRUE WHERE last_donation_date IS NOT NULL;
