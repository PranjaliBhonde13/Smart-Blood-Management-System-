-- ============================================================
-- 000_create_database.sql | Smart Blood Emergency Network
-- Bootstrap: creates the database and the application DB user.
-- Run by setup-database.ps1 as an admin account (e.g. root), which
-- replaces the {{PLACEHOLDERS}} below before executing.
--
-- Tables and seed data are NOT created here: they come from the Flyway
-- migrations in backend/src/main/resources/db/migration (V1, V2, ...),
-- which setup-database.ps1 applies right after this file.
-- ============================================================

CREATE DATABASE IF NOT EXISTS `{{DB_NAME}}`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- App user used by the backend (DB_USERNAME / DB_PASSWORD in backend/.env).
-- Both 'localhost' and '127.0.0.1' are created because MariaDB treats
-- socket/named-pipe and TCP connections as different hosts.
CREATE USER IF NOT EXISTS '{{APP_USER}}'@'localhost' IDENTIFIED BY '{{APP_PASSWORD}}';
CREATE USER IF NOT EXISTS '{{APP_USER}}'@'127.0.0.1' IDENTIFIED BY '{{APP_PASSWORD}}';
ALTER USER '{{APP_USER}}'@'localhost' IDENTIFIED BY '{{APP_PASSWORD}}';
ALTER USER '{{APP_USER}}'@'127.0.0.1' IDENTIFIED BY '{{APP_PASSWORD}}';

-- Full rights on this database only (Flyway needs DDL rights to migrate).
GRANT ALL PRIVILEGES ON `{{DB_NAME}}`.* TO '{{APP_USER}}'@'localhost';
GRANT ALL PRIVILEGES ON `{{DB_NAME}}`.* TO '{{APP_USER}}'@'127.0.0.1';
FLUSH PRIVILEGES;
