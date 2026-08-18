# Smart Blood Emergency Network — Master Plan (End-to-End)

> Solution architecture, delivery roadmap, and engineering standards.
> Status: **Phase 1–2 (Understand + Design)**. No application code written yet.
> Owner: Pranjali Bhonde · MCA Sem-III · IMCC Pune

---

## 0. Decisions Locked

| Area | Decision | Rationale |
|------|----------|-----------|
| Frontend | **React 18 + Vite + Redux Toolkit + RTK Query + Tailwind** | SPA, cache-aware data layer, role-based routing; matches "enterprise" goal. |
| Backend | **Java 17 + Spring Boot 3.x** | Matches PDF; production-grade, mature security stack. |
| Database | **MySQL 8** (+ Flyway migrations) | Matches PDF; versioned schema. |
| Auth | **JWT access + refresh tokens**, Spring Security, BCrypt | Stateless, scalable, standard. |
| Maps | **Google Maps JS API + Geolocation** (real) | Radius/nearby search is a core differentiator. |
| Email | **Spring Mail / SMTP** (real) — OTP + alerts | Needed for verification + notifications. |
| SMS/WhatsApp | **Mocked behind interface** now | Twilio costs money; swap later. |
| Integration strategy | **Ports & Adapters** — every external service has an interface + a mock impl first | Build/test without paid keys; wire real keys via config. |
| Target | **Production platform** | Full security, Docker, CI, tests, audit logging. |

---

## 1. Project Understanding (Phase 1)

**One-liner:** A real-time platform that connects **Patients, Donors, Hospitals, Blood Banks, and Admins** so that during an emergency, a compatible blood unit or donor is found in minutes, not hours.

**Core value:** *Reduce time-to-blood.* Every design decision is judged against that.

**6 Actors:**
1. **Guest** — browse landing, register, view public info.
2. **Patient** — raises blood requests, uploads medical docs, tracks status.
3. **Donor** — sets availability + blood group + location, accepts/rejects requests, logs donations.
4. **Hospital** — verifies patient requests, manages emergency cases, searches donors/banks.
5. **Blood Bank** — manages inventory (stock per blood group), responds to requests.
6. **Admin** — manages all users/entities, verifies hospitals & banks, dashboards, reports, audit.

---

## 2. System Architecture (Phase 2)

### 2.1 High-Level (Logical)

```
                         ┌────────────────────────────┐
                         │      React SPA (Vite)       │
                         │  Redux Toolkit + RTK Query  │
                         │  Role-based routes/guards   │
                         └──────────────┬──────────────┘
                                        │ HTTPS / JSON (JWT)
                                        ▼
                    ┌───────────────────────────────────────┐
                    │        Spring Boot API Gateway         │
                    │  Security Filter (JWT) → Controllers   │
                    │  Validation → Service → Repository     │
                    └───┬───────────────┬───────────────┬────┘
                        │               │               │
                 ┌──────▼─────┐  ┌──────▼──────┐  ┌─────▼──────┐
                 │  MySQL 8   │  │ Notification│  │  Geo /     │
                 │ (Flyway)   │  │  Ports:     │  │  Matching  │
                 │            │  │ Email(real) │  │  Engine    │
                 │            │  │ SMS(mock)   │  │ (Haversine)│
                 └────────────┘  │ Push(mock)  │  └─────┬──────┘
                                 └─────────────┘        │
                                                 ┌──────▼───────┐
                                                 │ Google Maps  │
                                                 │  (frontend)  │
                                                 └──────────────┘
```

### 2.2 Layered Backend (Clean Architecture)

```
Controller  → thin, HTTP only, maps DTO ↔ Service
   ↓
DTO + Mapper (MapStruct) → no entity leaks to API
   ↓
Service (interface + impl) → business rules, transactions
   ↓
Repository (Spring Data JPA) → persistence
   ↓
Entity → maps to MySQL tables (never returned directly)
```

Cross-cutting: Security, Exception handling (`@RestControllerAdvice`), Validation, Logging (SLF4J + MDC request-id), Audit (JPA auditing), Scheduling (expiry/reminders), Config.

### 2.3 Deployment (Phase 10 target)

```
Docker Compose:  [react-nginx]  [spring-boot-api]  [mysql]  [mailhog(dev)]
CI: GitHub Actions → build + test + docker image
Prod option: single VM (Docker Compose) or Render/Railway (API) + Netlify/Vercel (SPA)
```

---

## 3. Database Design (Phase 3)

Production upgrades over the PDF's ER diagram:

- **Single-table auth via `users`** (role enum); role-specific profile tables (`patients`, `donors`, `hospitals`, `blood_banks`) 1:1 to `users`.
- **Audit columns** on every table: `created_at`, `updated_at`, `created_by`, `updated_by`.
- **Soft delete:** `is_deleted BOOLEAN DEFAULT FALSE` (+ filtered queries).
- **Geo columns:** `latitude DECIMAL(10,7)`, `longitude DECIMAL(10,7)` on donors, hospitals, blood_banks for radius search.
- **Refresh tokens** table for JWT rotation + revocation.
- **Verification tokens** (email OTP) table.
- **Indexes:** `blood_group`, `city`, `(latitude, longitude)`, `status`, FK columns.
- **Constraints:** FKs with sensible `ON DELETE`, unique on `email`, `license_number`.
- **Flyway** versioned migrations (`V1__init.sql`, `V2__...`), no `ddl-auto=update` in prod.

Full table dictionary + ER diagram → `docs/03-DATABASE.md` (delivered in Module 0).

---

## 4. API Design (Phase 4)

**Convention:** `/api/v1/<resource>`, REST verbs, DTO in/out, uniform error envelope:

```json
{ "timestamp": "...", "status": 400, "error": "Validation Failed",
  "message": "...", "path": "/api/v1/...", "fieldErrors": {...} }
```

**Domain groups:** `auth`, `users`, `patients`, `donors`, `hospitals`, `blood-banks`,
`blood-stock`, `blood-requests`, `donations`, `notifications`, `geo`, `admin`, `reports`.

Every endpoint documented (method, path, auth/role, request, response, errors, status) via **OpenAPI/Swagger** + a written spec in `docs/04-API.md`.

---

## 5–7. Frontend / Backend Structure

### Frontend (feature-based)
```
src/
  app/            store.js, rootReducer, apiSlice (RTK Query base)
  features/       auth/ patients/ donors/ hospitals/ bloodBanks/
                  requests/ donations/ notifications/ admin/ geo/
                  (each: components/ pages/ <feature>Api.js <feature>Slice.js)
  components/     ui/ (Button, Card, Table, Modal, Skeleton, EmptyState...)
                  layout/ (Sidebar, Topbar, PageHeader, Breadcrumb)
  routes/         AppRouter, ProtectedRoute, RoleGuard
  hooks/          useAuth, useGeolocation, useDebounce, usePagination
  services/       axios/base config, tokenService
  lib/ constants/ utils/ styles/
```

### Backend (package-by-feature)
```
com.sben
  ├─ config/        (Security, CORS, OpenAPI, Async, Scheduler)
  ├─ security/      (JwtFilter, JwtService, UserDetails, RoleGuards)
  ├─ common/        (BaseEntity, ApiError, exceptions, response wrappers)
  ├─ user/  patient/ donor/ hospital/ bloodbank/
  ├─ bloodstock/ bloodrequest/ donation/
  ├─ notification/  (port + email/sms/push adapters)
  ├─ geo/           (matching engine, haversine)
  ├─ admin/ report/ audit/
  (each feature: controller/ dto/ mapper/ service/ repository/ entity/)
```

---

## 8. Delivery Roadmap (Phase 8 — build order)

Built **module by module, approval-gated**. Each module ships: design note → code → tests → demo.

| # | Module | Delivers |
|---|--------|----------|
| **M0** | **Foundation** | Repo scaffold, Docker, MySQL+Flyway, base entities, exception/response framework, OpenAPI, React app shell + Tailwind + routing + store. Full DB schema + API spec docs. |
| **M1** | **Auth & Identity** | Register/Login, JWT + refresh, roles, email OTP verification, forgot/reset password. Landing, Login, Register, OTP, Forgot pages. |
| **M2** | **Profiles & RBAC** | Role-specific profiles (patient/donor/hospital/bank), profile completion, protected routes + role guards, settings. |
| **M3** | **Blood Requests** | Create/track requests, medical doc upload, status lifecycle, hospital verification. Patient + Hospital dashboards. |
| **M4** | **Inventory & Matching** | Blood bank stock CRUD, compatibility matrix, donor availability, geo radius search (Maps), nearby donors/banks. |
| **M5** | **Donations & Notifications** | Accept/reject → schedule → complete → close, donation history, email + in-app notifications (SMS mocked). |
| **M6** | **Admin & Reports** | Admin dashboard, user/entity management, hospital/bank verification, analytics charts, reports (PDF/CSV), audit logs. |
| **M7** | **Hardening** | Rate limiting, security review, a11y, dark mode, skeletons/empty/error states, e2e tests, CI/CD, deployment. |

---

## 9–10. Testing & Deployment

- **Testing:** JUnit 5 + Mockito (unit), Spring Boot Test + Testcontainers (integration), RTK Query + React Testing Library (frontend), Playwright (e2e), Postman collection.
- **Security:** JWT rotation, BCrypt, RBAC, `@Valid` everywhere, rate limiting (Bucket4j), CORS whitelist, parameterized queries (JPA), XSS-safe React, secrets via env.
- **Deployment:** Dockerized, GitHub Actions CI, Compose for one-VM prod, `.env` config, health checks, logging.

---

## Engineering Standards (apply to all modules)

- SOLID · DRY · KISS · Clean Architecture · REST standards.
- No entity leaks past the service layer — DTOs only.
- Javadoc on every class/public method; component-level comments on React.
- Reusable UI only — no duplicated components.
- Every PR-sized module: design note → code → tests → demo screenshot.

---

## Immediate Next Step

**Module 0 — Foundation.** On approval I will deliver, in order:
1. `docs/03-DATABASE.md` — full table dictionary, ER diagram, Flyway V1 schema.
2. `docs/04-API.md` — complete endpoint catalog.
3. Repo scaffold (backend + frontend + docker) — structure only, then code.

Reply **"start M0"** to begin, or tell me which module/doc to expand first.
