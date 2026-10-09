-- ============================================================
-- V6__fix_demo_password_hash.sql | Smart Blood Emergency Network
-- The BCrypt hash in V2__seed_dummy_users.sql does not match 'Pass@123', so
-- on a fresh database the five demo accounts could not sign in. (Earlier
-- builds hid this by resetting those passwords on every startup.)
-- Replace ONLY that known-bad hash with a verified 'Pass@123' hash (the one
-- V3 uses). Any password a user has since changed is left untouched.
-- ============================================================
UPDATE users
SET password_hash = '$2a$10$ZPuOzB/li7b2mnPQIa8mFuGDzojr9I1qyck4J3gAsygt6PnxciGqS'
WHERE password_hash = '$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0E1ebdC4adK.qO';
