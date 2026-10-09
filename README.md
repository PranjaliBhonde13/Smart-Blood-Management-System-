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
`DB_USERNAME`/`DB_PASSWORD` from the environment or an optional `backend/.env`;
with the script defaults above, no configuration is needed.

**Adding schema changes:** create a new file in
`backend/src/main/resources/db/migration` (e.g. `V3__add_x.sql`). Never edit an applied one.
Both the setup script and the backend (Flyway, on startup) pick it up.

## 2. Run the backend
```bash
cd backend
./mvnw spring-boot:run      # no .env needed: defaults match the database script
```
- API:      http://localhost:8080/api/v1/ping
- Swagger:  http://localhost:8080/swagger-ui.html
- Health:   http://localhost:8080/actuator/health

Demo accounts (password `Pass@123`): `admin@`, `patient@`, `donor@`, `hospital@`, `bloodbank@sben.com`.

### Email
Out of the box, emails are **not sent**: each one (including sign-up and password-reset
codes) is printed in the backend console, so registration works without any mail account.
Every email is also listed under **Admin → Email delivery**. Demo `@sben.com` accounts are
never emailed; to test real delivery, register with an address you own.

To send real email, create `backend/.env` from `backend/.env.example` (it is git-ignored —
never commit it) and set `MAIL_ENABLED=true` plus the SMTP settings. For Gmail,
`MAIL_PASSWORD` must be a Google **app password**. Ask the project owner for the shared
credentials privately; they are deliberately not in this public repository.

Optional: `ADMIN_EMAILS` (comma-separated) creates real administrator accounts at startup;
each sets a password through **Forgot password**.

## 3. Run the frontend
```bash
cd frontend
npm install      # first time only
npm run dev      # no .env needed: the API defaults to http://localhost:8080/api/v1
```
- App: http://localhost:5173
- Optional `frontend/.env` (see `.env.example`): `VITE_GOOGLE_MAPS_API_KEY` enables the map view.

---

## Repository layout
```
backend/    Spring Boot API (package-by-feature under com.sben)
frontend/   React SPA (feature-based under src/)
database/   One-command DB setup (setup-database.ps1 + bootstrap migration)
docs/       Architecture, database, and API documentation
```
