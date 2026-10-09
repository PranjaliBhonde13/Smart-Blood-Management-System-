-- ============================================================
-- V3__seed_indian_dummy_data.sql | Smart Blood Emergency Network
-- Indian-format dummy data: 1 admin + 5 users for each other role
-- (PATIENT, DONOR, HOSPITAL, BLOOD_BANK), spread across Mumbai, Pune,
-- Delhi, Bengaluru and Hyderabad, plus stock, requests, donations and
-- notifications so every dashboard has data.
--
-- The 5 accounts from V2 are kept (same emails and logins) and given Indian
-- names, +91 phone numbers and addresses; 16 new accounts are added.
-- Password for every account: Pass@123
-- Rows are linked by email lookups, not fixed ids, so this works no matter
-- which ids already exist.
-- ============================================================
SET NAMES utf8mb4;

SET @pwd = '$2a$10$ZPuOzB/li7b2mnPQIa8mFuGDzojr9I1qyck4J3gAsygt6PnxciGqS'; -- Pass@123

-- ---------- update the V2 accounts to the Indian format ----------
UPDATE users SET phone = '+91 98200 00001' WHERE email = 'admin@sben.com';
UPDATE users SET full_name = 'Rahul Sharma',            phone = '+91 98200 11111' WHERE email = 'patient@sben.com';
UPDATE users SET full_name = 'Amit Kulkarni',           phone = '+91 98200 22222' WHERE email = 'donor@sben.com';
UPDATE users SET full_name = 'Sanjeevani Multispeciality Hospital', phone = '+91 22 26543210' WHERE email = 'hospital@sben.com';
UPDATE users SET full_name = 'Jeevan Dhara Blood Bank', phone = '+91 22 24931100' WHERE email = 'bloodbank@sben.com';

UPDATE patients p JOIN users u ON u.id = p.user_id
   SET p.gender = 'MALE', p.age = 30,
       p.address = 'Flat 302, Shanti Niwas, Linking Road, Bandra West, Mumbai, Maharashtra 400050',
       p.emergency_contact = '+91 98200 11112'
 WHERE u.email = 'patient@sben.com';

UPDATE donors d JOIN users u ON u.id = d.user_id
   SET d.city = 'Mumbai', d.age = 28
 WHERE u.email = 'donor@sben.com';

UPDATE hospitals h JOIN users u ON u.id = h.user_id
   SET h.hospital_name = 'Sanjeevani Multispeciality Hospital',
       h.phone = '+91 22 26543210',
       h.address = 'Plot 12, S.V. Road, Andheri West',
       h.city = 'Mumbai', h.state = 'Maharashtra', h.pincode = '400058'
 WHERE u.email = 'hospital@sben.com';

UPDATE blood_banks b JOIN users u ON u.id = b.user_id
   SET b.bank_name = 'Jeevan Dhara Blood Bank',
       b.phone = '+91 22 24931100',
       b.address = '7 Dr. Annie Besant Road, Worli, Mumbai, Maharashtra 400018',
       b.city = 'Mumbai'
 WHERE u.email = 'bloodbank@sben.com';

-- ---------- new users (4 more per non-admin role) ----------
INSERT INTO users (full_name, email, phone, password_hash, role, is_email_verified, is_enabled, is_deleted) VALUES
  -- patients
  ('Priya Deshmukh',     'priya.deshmukh@sben.com',  '+91 98220 33441', @pwd, 'PATIENT', TRUE, TRUE, FALSE),
  ('Arjun Mehta',        'arjun.mehta@sben.com',     '+91 98110 45672', @pwd, 'PATIENT', TRUE, TRUE, FALSE),
  ('Sneha Iyer',         'sneha.iyer@sben.com',      '+91 98450 67213', @pwd, 'PATIENT', TRUE, TRUE, FALSE),
  ('Mohammed Faizal',    'mohammed.faizal@sben.com', '+91 98490 78124', @pwd, 'PATIENT', TRUE, TRUE, FALSE),
  -- donors
  ('Neha Joshi',         'neha.joshi@sben.com',      '+91 97650 12098', @pwd, 'DONOR', TRUE, TRUE, FALSE),
  ('Rohan Gupta',        'rohan.gupta@sben.com',     '+91 99100 23987', @pwd, 'DONOR', TRUE, TRUE, FALSE),
  ('Kavya Nair',         'kavya.nair@sben.com',      '+91 99860 34876', @pwd, 'DONOR', TRUE, TRUE, FALSE),
  ('Venkatesh Rao',      'venkatesh.rao@sben.com',   '+91 99490 45765', @pwd, 'DONOR', TRUE, TRUE, FALSE),
  -- hospitals
  ('Shree Sai Hospital',             'shreesai.hospital@sben.com',  '+91 20 25534400', @pwd, 'HOSPITAL', TRUE, TRUE, FALSE),
  ('Aarogya Care Hospital',          'aarogya.hospital@sben.com',   '+91 11 26587700', @pwd, 'HOSPITAL', TRUE, TRUE, FALSE),
  ('Malleshwaram Medical Centre',    'malleshwaram.hospital@sben.com', '+91 80 23345600', @pwd, 'HOSPITAL', TRUE, TRUE, FALSE),
  ('Charminar Lifeline Hospital',    'charminar.hospital@sben.com', '+91 40 24568800', @pwd, 'HOSPITAL', TRUE, TRUE, FALSE),
  -- blood banks
  ('Raktdaan Kendra Pune',           'raktdaan.pune@sben.com',      '+91 20 26123300', @pwd, 'BLOOD_BANK', TRUE, TRUE, FALSE),
  ('Sanjivani Blood Centre',         'sanjivani.delhi@sben.com',    '+91 11 23364400', @pwd, 'BLOOD_BANK', TRUE, TRUE, FALSE),
  ('Namma Blood Bank',               'namma.bengaluru@sben.com',    '+91 80 22225500', @pwd, 'BLOOD_BANK', TRUE, TRUE, FALSE),
  ('Deccan Blood Bank',              'deccan.hyderabad@sben.com',   '+91 40 27616600', @pwd, 'BLOOD_BANK', TRUE, TRUE, FALSE);

-- ---------- patient profiles ----------
INSERT INTO patients (user_id, blood_group, age, gender, address, emergency_contact, is_deleted)
SELECT u.id, v.bg, v.age, v.gender, v.addr, v.ec, FALSE
  FROM users u JOIN (
    SELECT 'priya.deshmukh@sben.com' AS email, 'B+' AS bg, 26 AS age, 'FEMALE' AS gender,
           'B-14, Sukh Sagar Society, Kothrud, Pune, Maharashtra 411038' AS addr, '+91 98220 33442' AS ec
    UNION ALL SELECT 'arjun.mehta@sben.com', 'AB+', 45, 'MALE',
           'H.No. 221, Sector 15, Rohini, New Delhi, Delhi 110089', '+91 98110 45673'
    UNION ALL SELECT 'sneha.iyer@sben.com', 'O-', 34, 'FEMALE',
           '#45, 3rd Cross, 8th Main, Malleshwaram, Bengaluru, Karnataka 560003', '+91 98450 67214'
    UNION ALL SELECT 'mohammed.faizal@sben.com', 'A-', 52, 'MALE',
           '16-2-705, Malakpet, Hyderabad, Telangana 500036', '+91 98490 78125'
  ) v ON v.email = u.email;

-- ---------- donor profiles ----------
INSERT INTO donors (user_id, blood_group, age, city, latitude, longitude, last_donation_date, availability, is_deleted)
SELECT u.id, v.bg, v.age, v.city, v.lat, v.lng, v.last_don, v.avail, FALSE
  FROM users u JOIN (
    SELECT 'neha.joshi@sben.com' AS email, 'O+' AS bg, 24 AS age, 'Pune' AS city,
           18.5074000 AS lat, 73.8077000 AS lng, CURRENT_DATE - INTERVAL 120 DAY AS last_don, 'AVAILABLE' AS avail
    UNION ALL SELECT 'rohan.gupta@sben.com', 'B+', 31, 'Delhi',
           28.7041000, 77.1025000, CURRENT_DATE - INTERVAL 200 DAY, 'AVAILABLE'
    UNION ALL SELECT 'kavya.nair@sben.com', 'O-', 27, 'Bengaluru',
           12.9784000, 77.6408000, CURRENT_DATE - INTERVAL 30 DAY, 'UNAVAILABLE'
    UNION ALL SELECT 'venkatesh.rao@sben.com', 'AB-', 38, 'Hyderabad',
           17.4399000, 78.4983000, NULL, 'AVAILABLE'
  ) v ON v.email = u.email;

-- ---------- hospital profiles ----------
INSERT INTO hospitals (user_id, hospital_name, license_number, phone, address, city, state, pincode, latitude, longitude, is_verified, is_deleted)
SELECT u.id, u.full_name, v.lic, u.phone, v.addr, v.city, v.state, v.pin, v.lat, v.lng, v.verified, FALSE
  FROM users u JOIN (
    SELECT 'shreesai.hospital@sben.com' AS email, 'MH-PUN-HOSP-2041' AS lic,
           '1182 Fergusson College Road, Shivajinagar' AS addr, 'Pune' AS city, 'Maharashtra' AS state, '411004' AS pin,
           18.5236000 AS lat, 73.8411000 AS lng, TRUE AS verified
    UNION ALL SELECT 'aarogya.hospital@sben.com', 'DL-NDL-HOSP-3307',
           'B-2, Safdarjung Enclave', 'New Delhi', 'Delhi', '110029', 28.5649000, 77.1968000, TRUE
    UNION ALL SELECT 'malleshwaram.hospital@sben.com', 'KA-BLR-HOSP-1189',
           '15th Cross, Sampige Road, Malleshwaram', 'Bengaluru', 'Karnataka', '560003', 13.0035000, 77.5709000, TRUE
    UNION ALL SELECT 'charminar.hospital@sben.com', 'TS-HYD-HOSP-4420',
           'Near Gulzar Houz, Charminar', 'Hyderabad', 'Telangana', '500002', 17.3616000, 78.4747000, FALSE
  ) v ON v.email = u.email;

-- ---------- blood bank profiles ----------
INSERT INTO blood_banks (user_id, bank_name, license_number, phone, address, city, latitude, longitude, is_verified, is_deleted)
SELECT u.id, u.full_name, v.lic, u.phone, v.addr, v.city, v.lat, v.lng, v.verified, FALSE
  FROM users u JOIN (
    SELECT 'raktdaan.pune@sben.com' AS email, 'BB-MH-2018-0412' AS lic,
           '42 Karve Road, Erandwane, Pune, Maharashtra 411004' AS addr, 'Pune' AS city,
           18.5089000 AS lat, 73.8302000 AS lng, TRUE AS verified
    UNION ALL SELECT 'sanjivani.delhi@sben.com', 'BB-DL-2016-0877',
           '9 Connaught Place, New Delhi, Delhi 110001', 'Delhi', 28.6315000, 77.2167000, TRUE
    UNION ALL SELECT 'namma.bengaluru@sben.com', 'BB-KA-2019-0255',
           '88 Residency Road, Shanthala Nagar, Bengaluru, Karnataka 560025', 'Bengaluru', 12.9682000, 77.6033000, TRUE
    UNION ALL SELECT 'deccan.hyderabad@sben.com', 'BB-TS-2020-0631',
           '6-3-249 Road No. 1, Banjara Hills, Hyderabad, Telangana 500034', 'Hyderabad', 17.4126000, 78.4482000, FALSE
  ) v ON v.email = u.email;

-- ---------- blood stock for the 4 new banks (all 8 groups each) ----------
INSERT INTO blood_stock (blood_bank_id, blood_group, units_available, expiry_date, last_updated, is_deleted)
SELECT b.id, s.bg, s.units + b.id % 5, CURRENT_DATE + INTERVAL s.days DAY, NOW(), FALSE
  FROM blood_banks b JOIN users u ON u.id = b.user_id
  CROSS JOIN (
    SELECT 'A+' AS bg, 12 AS units, 30 AS days
    UNION ALL SELECT 'A-',   3, 28
    UNION ALL SELECT 'B+',  18, 32
    UNION ALL SELECT 'B-',   2, 25
    UNION ALL SELECT 'O+',  22, 35
    UNION ALL SELECT 'O-',   4, 20
    UNION ALL SELECT 'AB+',  7, 33
    UNION ALL SELECT 'AB-',  1, 15
  ) s
 WHERE u.email IN ('raktdaan.pune@sben.com', 'sanjivani.delhi@sben.com',
                   'namma.bengaluru@sben.com', 'deccan.hyderabad@sben.com');

-- ---------- id lookups for linked data ----------
SET @p_rahul  = (SELECT p.id FROM patients p JOIN users u ON u.id = p.user_id WHERE u.email = 'patient@sben.com');
SET @p_priya  = (SELECT p.id FROM patients p JOIN users u ON u.id = p.user_id WHERE u.email = 'priya.deshmukh@sben.com');
SET @p_arjun  = (SELECT p.id FROM patients p JOIN users u ON u.id = p.user_id WHERE u.email = 'arjun.mehta@sben.com');
SET @p_sneha  = (SELECT p.id FROM patients p JOIN users u ON u.id = p.user_id WHERE u.email = 'sneha.iyer@sben.com');
SET @p_faizal = (SELECT p.id FROM patients p JOIN users u ON u.id = p.user_id WHERE u.email = 'mohammed.faizal@sben.com');

SET @h_mum = (SELECT h.id FROM hospitals h JOIN users u ON u.id = h.user_id WHERE u.email = 'hospital@sben.com');
SET @h_pun = (SELECT h.id FROM hospitals h JOIN users u ON u.id = h.user_id WHERE u.email = 'shreesai.hospital@sben.com');
SET @h_del = (SELECT h.id FROM hospitals h JOIN users u ON u.id = h.user_id WHERE u.email = 'aarogya.hospital@sben.com');
SET @h_blr = (SELECT h.id FROM hospitals h JOIN users u ON u.id = h.user_id WHERE u.email = 'malleshwaram.hospital@sben.com');

SET @d_amit  = (SELECT d.id FROM donors d JOIN users u ON u.id = d.user_id WHERE u.email = 'donor@sben.com');
SET @d_neha  = (SELECT d.id FROM donors d JOIN users u ON u.id = d.user_id WHERE u.email = 'neha.joshi@sben.com');
SET @d_kavya = (SELECT d.id FROM donors d JOIN users u ON u.id = d.user_id WHERE u.email = 'kavya.nair@sben.com');

SET @b_mum = (SELECT b.id FROM blood_banks b JOIN users u ON u.id = b.user_id WHERE u.email = 'bloodbank@sben.com');
SET @b_blr = (SELECT b.id FROM blood_banks b JOIN users u ON u.id = b.user_id WHERE u.email = 'namma.bengaluru@sben.com');
SET @b_del = (SELECT b.id FROM blood_banks b JOIN users u ON u.id = b.user_id WHERE u.email = 'sanjivani.delhi@sben.com');

-- ---------- blood requests (one per lifecycle stage) ----------
INSERT INTO blood_requests (patient_id, hospital_id, blood_group, units_required, urgency, status, latitude, longitude, request_date, is_deleted) VALUES
  (@p_rahul,  @h_mum, 'O+',  2, 'HIGH',     'COMPLETED', 19.0596000, 72.8295000, NOW() - INTERVAL 20 DAY, FALSE),
  (@p_priya,  @h_pun, 'B+',  1, 'MEDIUM',   'VERIFIED',  18.5074000, 73.8077000, NOW() - INTERVAL 2 DAY,  FALSE),
  (@p_arjun,  @h_del, 'AB+', 3, 'CRITICAL', 'APPROVED',  28.7383000, 77.0822000, NOW() - INTERVAL 1 DAY,  FALSE),
  (@p_sneha,  @h_blr, 'O-',  2, 'HIGH',     'FULFILLED', 13.0035000, 77.5709000, NOW() - INTERVAL 5 DAY,  FALSE),
  (@p_faizal, NULL,   'A-',  1, 'LOW',      'PENDING',   17.3687000, 78.5247000, NOW() - INTERVAL 3 HOUR, FALSE),
  (@p_rahul,  @h_mum, 'O+',  1, 'MEDIUM',   'REJECTED',  19.0596000, 72.8295000, NOW() - INTERVAL 45 DAY, FALSE);

SET @r_rahul = (SELECT id FROM blood_requests WHERE patient_id = @p_rahul AND status = 'COMPLETED' ORDER BY id DESC LIMIT 1);
SET @r_arjun = (SELECT id FROM blood_requests WHERE patient_id = @p_arjun AND status = 'APPROVED'  ORDER BY id DESC LIMIT 1);
SET @r_sneha = (SELECT id FROM blood_requests WHERE patient_id = @p_sneha AND status = 'FULFILLED' ORDER BY id DESC LIMIT 1);

-- ---------- donation history ----------
INSERT INTO donation_history (request_id, donor_id, blood_bank_id, hospital_id, units_donated, donation_date, status) VALUES
  (@r_rahul, @d_amit,  NULL,   @h_mum, 1, CURRENT_DATE - INTERVAL 18 DAY, 'COMPLETED'),
  (@r_rahul, NULL,     @b_mum, @h_mum, 1, CURRENT_DATE - INTERVAL 18 DAY, 'COMPLETED'),
  (@r_sneha, @d_kavya, NULL,   @h_blr, 1, CURRENT_DATE - INTERVAL 4 DAY,  'COMPLETED'),
  (@r_sneha, NULL,     @b_blr, @h_blr, 1, CURRENT_DATE - INTERVAL 4 DAY,  'COMPLETED'),
  (@r_arjun, NULL,     @b_del, @h_del, 3, CURRENT_DATE + INTERVAL 1 DAY,  'SCHEDULED'),
  (@r_arjun, @d_neha,  NULL,   @h_del, 1, CURRENT_DATE + INTERVAL 2 DAY,  'CANCELLED');

-- ---------- notifications ----------
INSERT INTO notifications (user_id, title, message, type, status, created_at)
SELECT u.id, v.title, v.msg, v.type, v.status, NOW() - INTERVAL v.hrs HOUR
  FROM users u JOIN (
    SELECT 'patient@sben.com' AS email, 'Request completed' AS title,
           'Your O+ blood request at Sanjeevani Multispeciality Hospital has been completed.' AS msg,
           'REQUEST' AS type, 'READ' AS status, 400 AS hrs
    UNION ALL SELECT 'priya.deshmukh@sben.com', 'Request verified',
           'Shree Sai Hospital has verified your B+ blood request.', 'REQUEST', 'UNREAD', 40
    UNION ALL SELECT 'arjun.mehta@sben.com', 'Request approved',
           'Your CRITICAL AB+ request has been approved. Sanjivani Blood Centre is arranging 3 units.', 'REQUEST', 'UNREAD', 20
    UNION ALL SELECT 'sneha.iyer@sben.com', 'Blood arranged',
           'Your O- request has been fulfilled at Malleshwaram Medical Centre.', 'REQUEST', 'UNREAD', 110
    UNION ALL SELECT 'donor@sben.com', 'Thank you for donating',
           'Your donation helped a patient in Mumbai. You can donate again after 90 days.', 'DONATION', 'READ', 430
    UNION ALL SELECT 'neha.joshi@sben.com', 'Urgent: AB+ needed in Delhi',
           'A critical request near you needs blood. Open the app to respond.', 'DONATION', 'UNREAD', 20
    UNION ALL SELECT 'charminar.hospital@sben.com', 'Verification pending',
           'Your hospital registration is awaiting admin verification.', 'VERIFICATION', 'UNREAD', 72
    UNION ALL SELECT 'deccan.hyderabad@sben.com', 'Verification pending',
           'Your blood bank registration is awaiting admin verification.', 'VERIFICATION', 'UNREAD', 70
    UNION ALL SELECT 'admin@sben.com', '2 registrations awaiting verification',
           'Charminar Lifeline Hospital and Deccan Blood Bank are waiting for approval.', 'SYSTEM', 'UNREAD', 70
  ) v ON v.email = u.email;
