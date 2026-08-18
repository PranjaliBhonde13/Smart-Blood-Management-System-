# Database Design — Smart Blood Emergency Network

> Module 0, Step 1. MySQL 8, versioned with Flyway. Production-grade: audit columns,
> soft delete, geo indexing, token tables, referential integrity.
> This supersedes the PDF ER diagram (which is the academic baseline).

---

## 1. Design Principles

1. **Auth is centralized.** One `users` table holds credentials + role. Role-specific
   data lives in 1:1 profile tables (`patients`, `donors`, `hospitals`, `blood_banks`).
   *Why:* one login path, no duplicated auth columns, clean RBAC.
2. **Every table is auditable.** `created_at`, `updated_at`, `created_by`, `updated_by`.
   *Why:* healthcare data needs a paper trail; drives audit logs + reports.
3. **Nothing is hard-deleted.** `is_deleted` flag + filtered reads.
   *Why:* regulatory safety, undo, referential integrity of history.
4. **Geo is first-class.** `latitude`/`longitude` on donors, hospitals, banks + composite
   index. *Why:* radius/nearby search is the product's core differentiator.
5. **Tokens are tables, not memory.** `refresh_tokens`, `verification_tokens`.
   *Why:* rotation, revocation, OTP expiry survive restarts and scale horizontally.
6. **Enums as strings** (checked/constrained), not magic ints. *Why:* readable dumps.

---

## 2. Entity Relationship Overview

```
                         ┌───────────┐
                         │   users   │ (role, credentials)
                         └─────┬─────┘
        ┌──────────┬──────────┼──────────┬──────────────┐
     1:1│       1:1│       1:1│       1:1 │           1:N│
   ┌────▼───┐ ┌────▼────┐ ┌──▼──────┐ ┌──▼────────┐ ┌───▼───────────┐
   │patients│ │ donors  │ │hospitals│ │blood_banks│ │refresh_tokens │
   └───┬────┘ └────┬────┘ └────┬────┘ └─────┬─────┘ └───────────────┘
       │           │           │            │
       │1:N        │           │            │1:N
   ┌───▼──────────────┐        │        ┌───▼────────┐
   │  blood_requests  │◄───────┘ (hospital verifies) │blood_stock │
   └───┬──────────────┘   handled_by      └────────────┘
       │1:N
   ┌───▼──────────────┐   (donor + bank + hospital fulfil)
   │ donation_history │◄──────────────────────────────────
   └──────────────────┘

   users 1:N notifications        users 1:N verification_tokens
   admin actions → audit_logs
```

Compatibility (application-enforced, documented for reference):

| Recipient | Can receive from |
|-----------|------------------|
| O−  | O− |
| O+  | O−, O+ |
| A−  | O−, A− |
| A+  | O−, O+, A−, A+ |
| B−  | O−, B− |
| B+  | O−, O+, B−, B+ |
| AB− | O−, A−, B−, AB− |
| AB+ | everyone (universal recipient) |

---

## 3. Table Dictionary

### 3.1 `users` — central identity
| Column | Type | Key / Constraint | Why it exists |
|--------|------|------------------|---------------|
| id | BIGINT | PK, auto | Surrogate key. |
| full_name | VARCHAR(100) | NOT NULL | Display name. |
| email | VARCHAR(150) | UNIQUE, NOT NULL | Login id + notifications. |
| phone | VARCHAR(15) | | Contact / future SMS. |
| password_hash | VARCHAR(255) | NOT NULL | BCrypt hash, never plaintext. |
| role | ENUM(PATIENT,DONOR,HOSPITAL,BLOOD_BANK,ADMIN) | NOT NULL | RBAC discriminator. |
| is_email_verified | BOOLEAN | DEFAULT FALSE | Gate access until OTP confirmed. |
| is_enabled | BOOLEAN | DEFAULT TRUE | Admin can suspend accounts. |
| is_deleted | BOOLEAN | DEFAULT FALSE | Soft delete. |
| created_at / updated_at | DATETIME | | Audit. |
| created_by / updated_by | BIGINT | | Audit (actor user id). |

### 3.2 `patients` (1:1 users)
| Column | Type | Key | Why |
|--------|------|-----|-----|
| id | BIGINT | PK | |
| user_id | BIGINT | FK→users, UNIQUE | 1:1 link. |
| blood_group | VARCHAR(5) | | Requested group default. |
| age | INT | | Eligibility/clinical. |
| gender | VARCHAR(10) | | Clinical. |
| address | VARCHAR(255) | | Delivery/context. |
| emergency_contact | VARCHAR(15) | | Next-of-kin. |
| + audit + is_deleted | | | |

### 3.3 `donors` (1:1 users)
| Column | Type | Key | Why |
|--------|------|-----|-----|
| id | BIGINT | PK | |
| user_id | BIGINT | FK→users, UNIQUE | 1:1 link. |
| blood_group | VARCHAR(5) | NOT NULL | Matching. |
| age | INT | | Eligibility. |
| city | VARCHAR(50) | | Coarse filter. |
| latitude / longitude | DECIMAL(10,7) | INDEX | Radius search. |
| last_donation_date | DATE | | 90-day eligibility rule. |
| availability | ENUM(AVAILABLE,UNAVAILABLE) | DEFAULT AVAILABLE | Live status. |
| + audit + is_deleted | | | |

### 3.4 `hospitals` (1:1 users)
id, user_id(FK,UNIQUE), hospital_name, license_number(UNIQUE), phone, address,
city, state, pincode, latitude, longitude, **is_verified** (admin approves), + audit + soft delete.

### 3.5 `blood_banks` (1:1 users)
id, user_id(FK,UNIQUE), bank_name, license_number(UNIQUE), phone, address, city,
latitude, longitude, **is_verified**, + audit + soft delete.

### 3.6 `blood_stock` (N:1 blood_banks)
| Column | Type | Key | Why |
|--------|------|-----|-----|
| id | BIGINT | PK | |
| blood_bank_id | BIGINT | FK→blood_banks | Owner. |
| blood_group | VARCHAR(5) | NOT NULL | Inventory line. |
| units_available | INT | DEFAULT 0, CHECK ≥0 | Stock count. |
| expiry_date | DATE | | Scheduler flags near-expiry. |
| last_updated | DATETIME | | Freshness. |
| UNIQUE(blood_bank_id, blood_group) | | | One line per group per bank. |
| + audit + is_deleted | | | |

### 3.7 `blood_requests` (N:1 patients, N:1 hospitals)
| Column | Type | Key | Why |
|--------|------|-----|-----|
| id | BIGINT | PK | |
| patient_id | BIGINT | FK→patients | Raiser. |
| hospital_id | BIGINT | FK→hospitals, NULL | Verifier (assigned/chosen). |
| blood_group | VARCHAR(5) | NOT NULL | Needed group. |
| units_required | INT | NOT NULL, CHECK ≥1 | Quantity. |
| urgency | ENUM(LOW,MEDIUM,HIGH,CRITICAL) | | Triage/sort. |
| medical_report | VARCHAR(255) | | Uploaded doc path. |
| status | ENUM(PENDING,VERIFIED,APPROVED,FULFILLED,COMPLETED,REJECTED,CANCELLED) | | Lifecycle. |
| latitude / longitude | DECIMAL(10,7) | | Where blood is needed (geo match). |
| request_date | DATETIME | | SLA/reporting. |
| + audit + is_deleted | | | |

### 3.8 `donation_history` (N:1 request/donor/bank/hospital)
id, request_id(FK), donor_id(FK,NULL), blood_bank_id(FK,NULL), hospital_id(FK,NULL),
units_donated INT, donation_date DATE, status ENUM(SCHEDULED,COMPLETED,CANCELLED), + audit.
*Why NULLs:* a fulfilment may come from a donor **or** a bank.

### 3.9 `notifications`
id, user_id(FK→users), title, message TEXT, type ENUM(REQUEST,DONATION,SYSTEM,VERIFICATION),
status ENUM(READ,UNREAD) DEFAULT UNREAD, created_at.

### 3.10 `refresh_tokens`
id, user_id(FK), token VARCHAR(255) UNIQUE, expires_at DATETIME, revoked BOOLEAN, created_at.
*Why:* JWT refresh rotation + logout-everywhere.

### 3.11 `verification_tokens` (email OTP + password reset)
id, user_id(FK), token VARCHAR(10), type ENUM(EMAIL_OTP,PASSWORD_RESET),
expires_at DATETIME, used BOOLEAN DEFAULT FALSE, created_at.

### 3.12 `audit_logs`
id, actor_user_id(FK,NULL), action VARCHAR(100), entity_type VARCHAR(50),
entity_id BIGINT, details TEXT, ip_address VARCHAR(45), created_at.
*Why:* admin audit trail, security forensics.

---

## 4. Flyway Migration — `V1__initial_schema.sql`

> Lives at `backend/src/main/resources/db/migration/V1__initial_schema.sql`.
> Presented here for review; committed with the backend scaffold.

```sql
-- ============================================================
-- V1__initial_schema.sql  |  Smart Blood Emergency Network
-- MySQL 8. Charset utf8mb4. Engine InnoDB.
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
```

`V2__seed_admin.sql` (later) seeds the default admin + reference data.

---

## 5. Notes for reviewers
- MySQL 8 has no native spatial radius helper in our query style, so nearby search uses a
  **bounding-box prefilter on `(latitude, longitude)` index + Haversine** in SQL/Java.
  If we later need heavy geo, migrate these columns to `POINT` + `SPATIAL INDEX`.
- All timestamps are server-local `DATETIME`; if multi-region is ever needed, switch to
  `TIMESTAMP` (UTC) — noted as a known future decision, not needed now.

**Next in M0:** `docs/04-API.md` (endpoint catalog), then repo scaffold.
