# Smart Blood Emergency Network

Full-stack healthcare platform connecting patients, donors, hospitals and blood banks
for fast emergency blood matching.

- **Backend:** Java 21 · Spring Boot 3 · MySQL 8 · Flyway · JWT (from M1)
- **Frontend:** React 18 · Vite · Redux Toolkit · RTK Query · Tailwind
- **Docs:** see [`docs/`](docs/) — master plan, database, API.

## Project status
Module 0 (Foundation) complete: both apps build and run; schema, API catalog, and app
shell are in place. See [`docs/00-MASTER-PLAN.md`](docs/00-MASTER-PLAN.md) for the roadmap.

---

## Prerequisites
- Java 21 (installed ✓)
- Node 18+ (installed ✓)
- MySQL 8 — **see setup below**
- Maven — not needed; use the bundled wrapper (`./mvnw`).

## 1. Database setup (one command)
Start MySQL/MariaDB (XAMPP: click **Start** next to MySQL), then from the repo root:
```powershell
.\database\setup-database.ps1        # or double-click database\setup-database.cmd
```
This creates the `SBN` database, an app user `sben`/`sben`, all tables (Flyway `V1`)
and the seed users (`V2`, password `Pass@123` for `admin@`, `patient@`, `donor@`,
`hospital@`, `bloodbank@sben.com`). It is safe to re-run — applied migrations are skipped.

Options: `-Port 3309`, `-AdminUser root -AdminPassword <pw>`, `-AppPassword <pw>`,
`-Reset` (drop and rebuild). The backend reads `DB_HOST`/`DB_PORT`/`DB_NAME`/
`DB_USERNAME`/`DB_PASSWORD` from `backend/.env`.

**Adding schema changes:** create a new file in
`backend/src/main/resources/db/migration` (e.g. `V3__add_x.sql`). Never edit an applied one.
Both the setup script and the backend (Flyway, on startup) pick it up.

## 2. Run the backend
```bash
cd backend
# DB settings come from backend/.env (copy .env.example if missing).
./mvnw spring-boot:run
```
- API:      http://localhost:8080/api/v1/ping
- Swagger:  http://localhost:8080/swagger-ui.html
- Health:   http://localhost:8080/actuator/health

### Enable real OTP email with Brevo SMTP
By default, `MAIL_ENABLED=false`, so OTP emails are printed in the backend logs (via `MockEmailService`).
To send real OTP emails through Brevo SMTP:

1. Obtain your SMTP Key and verified Sender Email from [Brevo Dashboard](https://app.brevo.com/).
2. Copy `backend/.env.example` to `.env` or set environment variables before running the backend:

```powershell
$env:MAIL_ENABLED="true"
$env:MAIL_HOST="smtp-relay.brevo.com"
$env:MAIL_PORT="587"
$env:MAIL_USERNAME="pranjalibhonde13@gmail.com"
$env:MAIL_PASSWORD="your-brevo-smtp-key"
$env:MAIL_FROM="Smart Blood Emergency Network <pranjalibhonde13@gmail.com>"
./mvnw spring-boot:run
```

Brevo notes:
- Use the SMTP login from Brevo SMTP & API settings.
- Use a Brevo SMTP key (not your account password or REST API key).
- Ensure `MAIL_FROM` matches your verified sender email/domain in Brevo.
- Emails will send as styled HTML with standard plain-text fallback.


## 3. Run the frontend
```bash
cd frontend
cp .env.example .env
npm install      # first time only
npm run dev
```
- App: http://localhost:5173

---

## Repository layout
```
backend/    Spring Boot API (package-by-feature under com.sben)
frontend/   React SPA (feature-based under src/)
database/   One-command DB setup (setup-database.ps1 + bootstrap migration)
docs/       Architecture, database, and API documentation
```
