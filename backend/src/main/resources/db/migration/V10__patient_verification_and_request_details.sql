-- ============================================================
-- V10__patient_verification_and_request_details.sql | Smart Blood Emergency Network
-- 1. Patient profile: date of birth (source of age), full address, optional
--    coordinates, emergency contact name, notification consent and privacy
--    acknowledgement. Gender widened to hold "UNSPECIFIED".
-- 2. patient_documents: identity and blood-group proof, kept forever for
--    auditing. A replacement only flips is_current; old rows are never
--    deleted. Identity and blood-group verification are independent: each is
--    reviewed separately, and only a blood-group document records the group
--    the reviewer confirmed. No identity-document NUMBERS are collected.
-- 3. blood_requests: who the request is for (self / another patient) with a
--    snapshot of the recipient's name and age, blood component, required-by
--    time, preferred hospital, where the requested group came from, and who
--    confirmed or corrected it.
-- Additive only: existing rows keep their values (new columns NULL). Requests
-- created before this migration have blood_group_source NULL and keep the
-- old approval flow.
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE patients
  MODIFY COLUMN gender VARCHAR(20) NULL,
  ADD COLUMN date_of_birth           DATE          NULL AFTER blood_group,
  ADD COLUMN city                    VARCHAR(50)   NULL AFTER address,
  ADD COLUMN state                   VARCHAR(50)   NULL AFTER city,
  ADD COLUMN pincode                 VARCHAR(6)    NULL AFTER state,
  ADD COLUMN latitude                DECIMAL(10,7) NULL AFTER pincode,
  ADD COLUMN longitude               DECIMAL(10,7) NULL AFTER latitude,
  ADD COLUMN emergency_contact_name  VARCHAR(100)  NULL AFTER longitude,
  ADD COLUMN notification_consent    BOOLEAN       NULL AFTER emergency_contact,
  ADD COLUMN consent_updated_at      DATETIME      NULL AFTER notification_consent,
  ADD COLUMN privacy_acknowledged_at DATETIME      NULL AFTER consent_updated_at;

-- ---------- patient documents (full history) ----------
CREATE TABLE patient_documents (
  id                    BIGINT        NOT NULL AUTO_INCREMENT,
  patient_id            BIGINT        NOT NULL,
  category              VARCHAR(20)   NOT NULL,   -- IDENTITY | BLOOD_GROUP
  document_type         VARCHAR(40)   NOT NULL,
  original_filename     VARCHAR(255)  NOT NULL,
  stored_filename       VARCHAR(255)  NOT NULL,
  content_type          VARCHAR(50)   NOT NULL,
  file_size             BIGINT        NOT NULL,
  sha256                VARCHAR(64)   NOT NULL,
  status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  is_current            BOOLEAN       NOT NULL DEFAULT TRUE,
  uploaded_by           BIGINT        NOT NULL,
  uploaded_at           DATETIME      NOT NULL,
  reviewed_by           BIGINT        NULL,
  reviewed_at           DATETIME      NULL,
  review_remarks        VARCHAR(1000) NULL,
  confirmed_blood_group VARCHAR(5)    NULL,       -- group the reviewer read on a BLOOD_GROUP document
  superseded_at         DATETIME      NULL,
  is_deleted            BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by            BIGINT,
  updated_by            BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_pdoc_stored (stored_filename),
  KEY idx_pdoc_patient (patient_id, category, is_current),
  KEY idx_pdoc_status (status, is_current),
  CONSTRAINT fk_pdoc_patient  FOREIGN KEY (patient_id)  REFERENCES patients(id),
  CONSTRAINT fk_pdoc_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
  CONSTRAINT fk_pdoc_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- blood request details ----------
ALTER TABLE blood_requests
  ADD COLUMN request_for              VARCHAR(10)  NULL AFTER patient_id,
  ADD COLUMN recipient_name           VARCHAR(100) NULL AFTER request_for,
  ADD COLUMN recipient_age            INT          NULL AFTER recipient_name,
  ADD COLUMN recipient_relationship   VARCHAR(50)  NULL AFTER recipient_age,
  ADD COLUMN component                VARCHAR(30)  NULL AFTER blood_group,
  ADD COLUMN required_by              DATETIME     NULL AFTER urgency,
  ADD COLUMN preferred_hospital_id    BIGINT       NULL AFTER hospital_id,
  ADD COLUMN preferred_hospital_name  VARCHAR(100) NULL AFTER preferred_hospital_id,
  ADD COLUMN location_notes           VARCHAR(255) NULL AFTER longitude,
  ADD COLUMN blood_group_source       VARCHAR(20)  NULL AFTER component,
  ADD COLUMN blood_group_confirmed_by BIGINT       NULL AFTER blood_group_source,
  ADD COLUMN blood_group_confirmed_at DATETIME     NULL AFTER blood_group_confirmed_by,
  ADD CONSTRAINT fk_req_pref_hospital FOREIGN KEY (preferred_hospital_id) REFERENCES hospitals(id);
