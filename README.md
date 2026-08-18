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

## 1. Database setup (one-time)
Install MySQL 8, then create the database:
```sql
CREATE DATABASE sben CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
Flyway creates all tables automatically on first backend start.

## 2. Run the backend
```bash
cd backend
# Provide DB credentials via env (defaults: root/root):
#   Windows PowerShell:  $env:DB_USERNAME="root"; $env:DB_PASSWORD="yourpass"
./mvnw spring-boot:run
```
- API:      http://localhost:8080/api/v1/ping
- Swagger:  http://localhost:8080/swagger-ui.html
- Health:   http://localhost:8080/actuator/health

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
docs/       Architecture, database, and API documentation
```
