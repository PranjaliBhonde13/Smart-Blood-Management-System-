-- ============================================================
-- V2__seed_dummy_users.sql | Smart Blood Emergency Network
-- Seeds default test users for PATIENT, DONOR, HOSPITAL, BLOOD_BANK, and ADMIN.
-- Password for all accounts: Pass@123
-- BCrypt Hash for Pass@123: $2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0E1ebdC4adK.qO
-- ============================================================
SET NAMES utf8mb4;

-- 1. ADMIN USER
INSERT INTO users (id, full_name, email, phone, password_hash, role, is_email_verified, is_enabled, is_deleted)
VALUES (1, 'System Administrator', 'admin@sben.com', '9876543200', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0E1ebdC4adK.qO', 'ADMIN', TRUE, TRUE, FALSE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), is_email_verified = TRUE, is_enabled = TRUE;

-- 2. PATIENT USER & PROFILE
INSERT INTO users (id, full_name, email, phone, password_hash, role, is_email_verified, is_enabled, is_deleted)
VALUES (2, 'Patient User', 'patient@sben.com', '9876543210', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0E1ebdC4adK.qO', 'PATIENT', TRUE, TRUE, FALSE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), is_email_verified = TRUE, is_enabled = TRUE;

INSERT INTO patients (id, user_id, blood_group, age, gender, address, emergency_contact, is_deleted)
VALUES (1, 2, 'O+', 30, 'MALE', '123 Emergency St, Mumbai', '9876543211', FALSE)
ON DUPLICATE KEY UPDATE blood_group = VALUES(blood_group), age = VALUES(age);

-- 3. DONOR USER & PROFILE
INSERT INTO users (id, full_name, email, phone, password_hash, role, is_email_verified, is_enabled, is_deleted)
VALUES (3, 'Donor User', 'donor@sben.com', '9876543220', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0E1ebdC4adK.qO', 'DONOR', TRUE, TRUE, FALSE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), is_email_verified = TRUE, is_enabled = TRUE;

INSERT INTO donors (id, user_id, blood_group, age, city, latitude, longitude, last_donation_date, availability, is_deleted)
VALUES (1, 3, 'A+', 28, 'Mumbai', 19.0760000, 72.8777000, CURRENT_DATE - INTERVAL 90 DAY, 'AVAILABLE', FALSE)
ON DUPLICATE KEY UPDATE blood_group = VALUES(blood_group), availability = VALUES(availability);

-- 4. HOSPITAL USER & PROFILE
INSERT INTO users (id, full_name, email, phone, password_hash, role, is_email_verified, is_enabled, is_deleted)
VALUES (4, 'City General Hospital', 'hospital@sben.com', '9876543230', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0E1ebdC4adK.qO', 'HOSPITAL', TRUE, TRUE, FALSE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), is_email_verified = TRUE, is_enabled = TRUE;

INSERT INTO hospitals (id, user_id, hospital_name, license_number, phone, address, city, state, pincode, latitude, longitude, is_verified, is_deleted)
VALUES (1, 4, 'City General Hospital', 'HOSP-100200', '9876543230', '45 Medical Enclave', 'Mumbai', 'Maharashtra', '400001', 19.0800000, 72.8800000, TRUE, FALSE)
ON DUPLICATE KEY UPDATE is_verified = TRUE;

-- 5. BLOOD BANK USER, PROFILE & INVENTORY
INSERT INTO users (id, full_name, email, phone, password_hash, role, is_email_verified, is_enabled, is_deleted)
VALUES (5, 'Red Cross Central Blood Bank', 'bloodbank@sben.com', '9876543240', '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0E1ebdC4adK.qO', 'BLOOD_BANK', TRUE, TRUE, FALSE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), is_email_verified = TRUE, is_enabled = TRUE;

INSERT INTO blood_banks (id, user_id, bank_name, license_number, phone, address, city, latitude, longitude, is_verified, is_deleted)
VALUES (1, 5, 'Red Cross Central Blood Bank', 'BANK-300400', '9876543240', '88 Lifesaver Ave', 'Mumbai', 19.0850000, 72.8850000, TRUE, FALSE)
ON DUPLICATE KEY UPDATE is_verified = TRUE;

-- INITIAL INVENTORY STOCK FOR ALL 8 BLOOD GROUPS
INSERT INTO blood_stock (blood_bank_id, blood_group, units_available, expiry_date, last_updated, is_deleted)
VALUES 
  (1, 'A+',  15, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE),
  (1, 'A-',   5, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE),
  (1, 'B+',  20, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE),
  (1, 'B-',   4, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE),
  (1, 'O+',  30, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE),
  (1, 'O-',   8, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE),
  (1, 'AB+', 10, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE),
  (1, 'AB-',  3, CURRENT_DATE + INTERVAL 35 DAY, NOW(), FALSE)
ON DUPLICATE KEY UPDATE units_available = VALUES(units_available);
