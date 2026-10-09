-- ============================================================
-- V9__hospital_documents.sql | Smart Blood Emergency Network
-- Hospital documents & verification:
-- 1. Hospital registration category and whether it runs its own blood bank
--    (decides which documents apply), plus who verified it and when.
-- 2. hospital_document_requirements: which documents are required, per
--    registration category (NULL = every category) and condition. Admins can
--    change these rows; nothing about them is hard-coded in the application.
-- 3. hospital_document_requests: corrected or additional documents an
--    administrator asked a hospital for.
-- 4. hospital_documents: every uploaded file, kept forever for auditing. A
--    replacement or removal only flips is_current; old rows are never
--    deleted. Stored filenames point into the private upload directory,
--    which is never served statically.
-- Additive only: existing hospitals keep their current values. A hospital
-- that was verified before this migration has no documents yet, so it is not
-- treated as fully verified until its mandatory documents are approved (see
-- app.hospital-documents.legacy-grace-until for an optional transition window).
-- ============================================================
SET NAMES utf8mb4;

ALTER TABLE hospitals
  ADD COLUMN registration_category VARCHAR(40) NULL AFTER license_number,
  ADD COLUMN operates_blood_bank   BOOLEAN     NOT NULL DEFAULT FALSE AFTER registration_category,
  ADD COLUMN verified_at           DATETIME    NULL AFTER is_verified,
  ADD COLUMN verified_by           BIGINT      NULL AFTER verified_at;

-- ---------- requirements ----------
CREATE TABLE hospital_document_requirements (
  id                    BIGINT       NOT NULL AUTO_INCREMENT,
  document_type         VARCHAR(40)  NOT NULL,
  registration_category VARCHAR(40)  NULL,
  applies_when          VARCHAR(30)  NOT NULL DEFAULT 'ALWAYS',
  is_mandatory          BOOLEAN      NOT NULL DEFAULT TRUE,
  expiry_date_required  BOOLEAN      NOT NULL DEFAULT FALSE,
  description           VARCHAR(500) NULL,
  is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
  sort_order            INT          NOT NULL DEFAULT 0,
  is_deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by            BIGINT,
  updated_by            BIGINT,
  PRIMARY KEY (id),
  KEY idx_hdr_active (is_active, document_type)
) ENGINE=InnoDB;

-- Default rules. A blood bank license applies only to hospitals that operate
-- their own blood bank, never to hospitals that only request blood.
INSERT INTO hospital_document_requirements
  (document_type, registration_category, applies_when, is_mandatory, expiry_date_required, description, sort_order)
VALUES
  ('REGISTRATION_CERTIFICATE', NULL, 'ALWAYS', TRUE, FALSE,
   'Certificate of registration issued under the applicable clinical establishments or nursing home act.', 10),
  ('OPERATING_LICENSE', NULL, 'ALWAYS', TRUE, FALSE,
   'Current operating license, or other proof of registration that applies to your hospital category.', 20),
  ('BLOOD_BANK_LICENSE', NULL, 'OPERATES_BLOOD_BANK', TRUE, TRUE,
   'Blood bank / blood centre license. Required only if your hospital operates its own blood bank.', 30),
  ('ACCREDITATION_CERTIFICATE', NULL, 'ALWAYS', FALSE, FALSE,
   'Optional accreditation certificates (for example NABH or NABL).', 40),
  ('SUPPORTING_DOCUMENT', NULL, 'ALWAYS', FALSE, FALSE,
   'Optional supporting documents.', 50);

-- ---------- admin document requests ----------
CREATE TABLE hospital_document_requests (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  hospital_id   BIGINT        NOT NULL,
  document_type VARCHAR(40)   NOT NULL,
  title         VARCHAR(150)  NULL,
  reason        VARCHAR(1000) NOT NULL,
  status        VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
  requested_by  BIGINT        NOT NULL,
  requested_at  DATETIME      NOT NULL,
  resolved_by   BIGINT        NULL,
  resolved_at   DATETIME      NULL,
  is_deleted    BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by    BIGINT,
  updated_by    BIGINT,
  PRIMARY KEY (id),
  KEY idx_hdreq_hospital (hospital_id, status),
  CONSTRAINT fk_hdreq_hospital  FOREIGN KEY (hospital_id)  REFERENCES hospitals(id),
  CONSTRAINT fk_hdreq_requester FOREIGN KEY (requested_by) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- documents (full history) ----------
CREATE TABLE hospital_documents (
  id                     BIGINT        NOT NULL AUTO_INCREMENT,
  hospital_id            BIGINT        NOT NULL,
  document_type          VARCHAR(40)   NOT NULL,
  document_request_id    BIGINT        NULL,
  original_filename      VARCHAR(255)  NOT NULL,
  stored_filename        VARCHAR(255)  NOT NULL,
  content_type           VARCHAR(50)   NOT NULL,
  file_size              BIGINT        NOT NULL,
  sha256                 VARCHAR(64)   NOT NULL,
  issue_date             DATE          NULL,
  expiry_date            DATE          NULL,
  status                 VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
  is_current             BOOLEAN       NOT NULL DEFAULT TRUE,
  uploaded_by            BIGINT        NOT NULL,
  uploaded_at            DATETIME      NOT NULL,
  reviewed_by            BIGINT        NULL,
  reviewed_at            DATETIME      NULL,
  review_remarks         VARCHAR(1000) NULL,
  superseded_by          BIGINT        NULL,
  superseded_at          DATETIME      NULL,
  removed_by             BIGINT        NULL,
  removed_at             DATETIME      NULL,
  expiry_warning_sent_at DATETIME      NULL,
  is_deleted             BOOLEAN       NOT NULL DEFAULT FALSE,
  created_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by             BIGINT,
  updated_by             BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_hdoc_stored (stored_filename),
  KEY idx_hdoc_hospital (hospital_id, is_current),
  KEY idx_hdoc_status_expiry (status, expiry_date),
  CONSTRAINT fk_hdoc_hospital FOREIGN KEY (hospital_id)         REFERENCES hospitals(id),
  CONSTRAINT fk_hdoc_request  FOREIGN KEY (document_request_id) REFERENCES hospital_document_requests(id),
  CONSTRAINT fk_hdoc_uploader FOREIGN KEY (uploaded_by)         REFERENCES users(id),
  CONSTRAINT fk_hdoc_reviewer FOREIGN KEY (reviewed_by)         REFERENCES users(id)
) ENGINE=InnoDB;
