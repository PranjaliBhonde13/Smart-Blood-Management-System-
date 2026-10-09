-- ============================================================
-- V1__initial_schema.sql  |  Smart Blood Emergency Network
-- MySQL 8. Charset utf8mb4. Engine InnoDB.
-- See docs/03-DATABASE.md for the full table dictionary + rationale.
-- ============================================================
SET NAMES utf8mb4;

-- ---------- users ----------
CREATE TABLE users (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  full_name         VARCHAR(100) NOT NULL,
  email             VARCHAR(150) NOT NULL,
  phone             VARCHAR(15),
  password_hash     VARCHAR(255) NOT NULL,
  role              ENUM('PATIENT','DONOR','HOSPITAL','BLOOD_BANK','ADMIN') NOT NULL,
  is_email_verified BOOLEAN      NOT NULL DEFAULT FALSE,
  is_enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
  is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by        BIGINT,
  updated_by        BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_email (email),
  KEY idx_users_role (role)
) ENGINE=InnoDB;

-- ---------- patients ----------
CREATE TABLE patients (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  user_id           BIGINT NOT NULL,
  blood_group       VARCHAR(5),
  age               INT,
  gender            VARCHAR(10),
  address           VARCHAR(255),
  emergency_contact VARCHAR(15),
  is_deleted        BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by        BIGINT,
  updated_by        BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_patients_user (user_id),
  CONSTRAINT fk_patients_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- donors ----------
CREATE TABLE donors (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  user_id            BIGINT NOT NULL,
  blood_group        VARCHAR(5) NOT NULL,
  age                INT,
  city               VARCHAR(50),
  latitude           DECIMAL(10,7),
  longitude          DECIMAL(10,7),
  last_donation_date DATE,
  availability       ENUM('AVAILABLE','UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE',
  is_deleted         BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by         BIGINT,
  updated_by         BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_donors_user (user_id),
  KEY idx_donors_group (blood_group),
  KEY idx_donors_geo (latitude, longitude),
  KEY idx_donors_avail (availability),
  CONSTRAINT fk_donors_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- hospitals ----------
CREATE TABLE hospitals (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  hospital_name  VARCHAR(100) NOT NULL,
  license_number VARCHAR(50)  NOT NULL,
  phone          VARCHAR(15),
  address        VARCHAR(255),
  city           VARCHAR(50),
  state          VARCHAR(50),
  pincode        VARCHAR(10),
  latitude       DECIMAL(10,7),
  longitude      DECIMAL(10,7),
  is_verified    BOOLEAN  NOT NULL DEFAULT FALSE,
  is_deleted     BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by     BIGINT,
  updated_by     BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_hospitals_user (user_id),
  UNIQUE KEY uq_hospitals_license (license_number),
  KEY idx_hospitals_geo (latitude, longitude),
  CONSTRAINT fk_hospitals_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- blood_banks ----------
CREATE TABLE blood_banks (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  bank_name      VARCHAR(100) NOT NULL,
  license_number VARCHAR(50)  NOT NULL,
  phone          VARCHAR(15),
  address        VARCHAR(255),
  city           VARCHAR(50),
  latitude       DECIMAL(10,7),
  longitude      DECIMAL(10,7),
  is_verified    BOOLEAN  NOT NULL DEFAULT FALSE,
  is_deleted     BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by     BIGINT,
  updated_by     BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_banks_user (user_id),
  UNIQUE KEY uq_banks_license (license_number),
  KEY idx_banks_geo (latitude, longitude),
  CONSTRAINT fk_banks_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- blood_stock ----------
CREATE TABLE blood_stock (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  blood_bank_id   BIGINT NOT NULL,
  blood_group     VARCHAR(5) NOT NULL,
  units_available INT NOT NULL DEFAULT 0,
  expiry_date     DATE,
  last_updated    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted      BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by      BIGINT,
  updated_by      BIGINT,
  PRIMARY KEY (id),
  UNIQUE KEY uq_stock_bank_group (blood_bank_id, blood_group),
  KEY idx_stock_group (blood_group),
  CONSTRAINT chk_stock_units CHECK (units_available >= 0),
  CONSTRAINT fk_stock_bank FOREIGN KEY (blood_bank_id) REFERENCES blood_banks(id)
) ENGINE=InnoDB;

-- ---------- blood_requests ----------
CREATE TABLE blood_requests (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  patient_id     BIGINT NOT NULL,
  hospital_id    BIGINT,
  blood_group    VARCHAR(5) NOT NULL,
  units_required INT NOT NULL,
  urgency        ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
  medical_report VARCHAR(255),
  status         ENUM('PENDING','VERIFIED','APPROVED','FULFILLED','COMPLETED','REJECTED','CANCELLED')
                 NOT NULL DEFAULT 'PENDING',
  latitude       DECIMAL(10,7),
  longitude      DECIMAL(10,7),
  request_date   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  is_deleted     BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by     BIGINT,
  updated_by     BIGINT,
  PRIMARY KEY (id),
  KEY idx_req_status (status),
  KEY idx_req_group (blood_group),
  KEY idx_req_geo (latitude, longitude),
  CONSTRAINT chk_req_units CHECK (units_required >= 1),
  CONSTRAINT fk_req_patient  FOREIGN KEY (patient_id)  REFERENCES patients(id),
  CONSTRAINT fk_req_hospital FOREIGN KEY (hospital_id) REFERENCES hospitals(id)
) ENGINE=InnoDB;

-- ---------- donation_history ----------
CREATE TABLE donation_history (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  request_id    BIGINT NOT NULL,
  donor_id      BIGINT,
  blood_bank_id BIGINT,
  hospital_id   BIGINT,
  units_donated INT NOT NULL DEFAULT 1,
  donation_date DATE,
  status        ENUM('SCHEDULED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by    BIGINT,
  updated_by    BIGINT,
  PRIMARY KEY (id),
  KEY idx_don_status (status),
  CONSTRAINT fk_don_request  FOREIGN KEY (request_id)    REFERENCES blood_requests(id),
  CONSTRAINT fk_don_donor    FOREIGN KEY (donor_id)      REFERENCES donors(id),
  CONSTRAINT fk_don_bank     FOREIGN KEY (blood_bank_id) REFERENCES blood_banks(id),
  CONSTRAINT fk_don_hospital FOREIGN KEY (hospital_id)   REFERENCES hospitals(id)
) ENGINE=InnoDB;

-- ---------- notifications ----------
CREATE TABLE notifications (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  title      VARCHAR(150) NOT NULL,
  message    TEXT,
  type       ENUM('REQUEST','DONATION','SYSTEM','VERIFICATION') NOT NULL DEFAULT 'SYSTEM',
  status     ENUM('READ','UNREAD') NOT NULL DEFAULT 'UNREAD',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_notif_user (user_id, status),
  CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- refresh_tokens ----------
CREATE TABLE refresh_tokens (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  token      VARCHAR(255) NOT NULL,
  expires_at DATETIME NOT NULL,
  revoked    BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_token (token),
  KEY idx_refresh_user (user_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- verification_tokens ----------
CREATE TABLE verification_tokens (
  id         BIGINT NOT NULL AUTO_INCREMENT,
  user_id    BIGINT NOT NULL,
  token      VARCHAR(10) NOT NULL,
  type       ENUM('EMAIL_OTP','PASSWORD_RESET') NOT NULL,
  expires_at DATETIME NOT NULL,
  used       BOOLEAN  NOT NULL DEFAULT FALSE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_vtoken_user (user_id, type),
  CONSTRAINT fk_vtoken_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

-- ---------- audit_logs ----------
CREATE TABLE audit_logs (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT,
  action        VARCHAR(100) NOT NULL,
  entity_type   VARCHAR(50),
  entity_id     BIGINT,
  details       TEXT,
  ip_address    VARCHAR(45),
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_actor (actor_user_id),
  KEY idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB;
