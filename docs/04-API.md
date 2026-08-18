# API Design — Smart Blood Emergency Network

> Module 0, Step 2. REST, `/api/v1`, JSON, JWT bearer auth.
> Live interactive docs via Swagger at `/swagger-ui.html` once the backend runs.

---

## 1. Conventions

- **Base:** `/api/v1`
- **Auth:** `Authorization: Bearer <access_token>` (except public auth endpoints).
- **Verbs:** GET (read), POST (create), PUT (full update), PATCH (partial), DELETE (soft delete).
- **Pagination:** `?page=0&size=20&sort=createdAt,desc` → returns Spring `Page` envelope.
- **Filtering:** query params, e.g. `?bloodGroup=O-&city=Pune&status=PENDING`.

### Success envelope
```json
{ "success": true, "message": "OK", "data": { ... } }
```
### Error envelope (from `@RestControllerAdvice`)
```json
{ "success": false, "timestamp": "2026-08-01T10:00:00Z", "status": 400,
  "error": "Validation Failed", "message": "Invalid input",
  "path": "/api/v1/auth/register", "fieldErrors": { "email": "must be a valid email" } }
```
### Status codes
200 OK · 201 Created · 204 No Content · 400 Bad Request · 401 Unauthorized ·
403 Forbidden · 404 Not Found · 409 Conflict (duplicate) · 422 Unprocessable · 429 Too Many Requests · 500 Server Error.

### Roles
`PATIENT` · `DONOR` · `HOSPITAL` · `BLOOD_BANK` · `ADMIN` · `PUBLIC` (no auth).

---

## 2. Auth & Identity  `/api/v1/auth`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| POST | `/register` | PUBLIC | Create account (role in body), send email OTP. |
| POST | `/verify-otp` | PUBLIC | Confirm email with OTP → activate. |
| POST | `/resend-otp` | PUBLIC | Re-issue OTP. |
| POST | `/login` | PUBLIC | Returns access + refresh tokens. |
| POST | `/refresh` | PUBLIC (valid refresh) | Rotate access token. |
| POST | `/logout` | AUTH | Revoke refresh token. |
| POST | `/forgot-password` | PUBLIC | Email reset token. |
| POST | `/reset-password` | PUBLIC | Set new password with token. |
| GET  | `/me` | AUTH | Current user + profile. |

**POST /register — request**
```json
{ "fullName":"Asha K","email":"asha@x.com","phone":"98...","password":"P@ss1234",
  "role":"DONOR" }
```
**201 response**
```json
{ "success":true,"message":"OTP sent to email",
  "data":{ "userId":12,"email":"asha@x.com","otpExpiresInSec":600 } }
```
**409** if email exists.

**POST /login — response**
```json
{ "success":true,"data":{
  "accessToken":"eyJ...","refreshToken":"a1b2...","tokenType":"Bearer",
  "expiresInSec":900,"user":{"id":12,"role":"DONOR","fullName":"Asha K","emailVerified":true}}}
```
**401** bad credentials · **403** email not verified.

---

## 3. Profiles  `/api/v1/patients` · `/donors` · `/hospitals` · `/blood-banks`

Pattern per role (owner or ADMIN):
| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| GET | `/{id}` | owner/ADMIN | Read profile. |
| PUT | `/{id}` | owner/ADMIN | Update profile (completion). |
| PATCH | `/donors/{id}/availability` | owner | Toggle AVAILABLE/UNAVAILABLE. |
| PATCH | `/donors/{id}/location` | owner | Update lat/long from Geolocation. |
| GET | `/donors` | HOSPITAL/ADMIN | List/search donors (filters + geo). |

**GET /donors (search) — query**
`?bloodGroup=O-&lat=18.52&lng=73.85&radiusKm=10&availability=AVAILABLE&page=0&size=20`
Returns donors inside the radius, sorted by distance (distance included per row).

---

## 4. Blood Requests  `/api/v1/blood-requests`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| POST | `/` | PATIENT | Create request (+ geo, urgency). |
| POST | `/{id}/report` | PATIENT | Upload medical document (multipart). |
| GET | `/{id}` | owner/HOSPITAL/ADMIN | Detail + status timeline. |
| GET | `/mine` | PATIENT | My requests (paged). |
| GET | `/` | HOSPITAL/ADMIN | Queue (filter status/urgency/group/geo). |
| PATCH | `/{id}/verify` | HOSPITAL | PENDING → VERIFIED. |
| PATCH | `/{id}/approve` | HOSPITAL/BLOOD_BANK | VERIFIED → APPROVED. |
| PATCH | `/{id}/status` | HOSPITAL/ADMIN | Move status (guarded transitions). |
| DELETE | `/{id}` | owner/ADMIN | Cancel (soft). |

**POST / — request**
```json
{ "bloodGroup":"O-","unitsRequired":2,"urgency":"CRITICAL",
  "latitude":18.5204,"longitude":73.8567 }
```
**Status machine (server-enforced):**
`PENDING → VERIFIED → APPROVED → FULFILLED → COMPLETED`; any → `REJECTED`/`CANCELLED`.
Invalid jumps → **422**.

---

## 5. Inventory  `/api/v1/blood-stock`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| GET | `/bank/{bankId}` | BLOOD_BANK owner/ADMIN | Bank inventory. |
| POST | `/` | BLOOD_BANK | Add stock line. |
| PUT | `/{id}` | BLOOD_BANK | Update units/expiry. |
| GET | `/search` | HOSPITAL/PATIENT/ADMIN | Find banks with a group in stock near geo. |

**GET /search — query** `?bloodGroup=O-&lat=..&lng=..&radiusKm=15` → banks with units>0, distance-sorted.

---

## 6. Matching / Geo  `/api/v1/geo`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| GET | `/nearby/donors` | PATIENT/HOSPITAL | Compatible + available donors in radius. |
| GET | `/nearby/blood-banks` | PATIENT/HOSPITAL | Banks with matching stock in radius. |
| GET | `/nearby/hospitals` | PATIENT | Verified hospitals in radius. |

Matching = **blood-group compatibility matrix** (see DB doc §2) **+** Haversine distance **+** `availability=AVAILABLE` **+** donor 90-day eligibility.

---

## 7. Donations  `/api/v1/donations`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| POST | `/{requestId}/accept` | DONOR | Accept → creates SCHEDULED donation, notifies patient/hospital. |
| POST | `/{requestId}/reject` | DONOR | Decline (logged). |
| PATCH | `/{id}/complete` | HOSPITAL/BLOOD_BANK | Mark COMPLETED, decrement stock, close request. |
| GET | `/mine` | DONOR | My donation history. |
| GET | `/request/{requestId}` | HOSPITAL/ADMIN | Fulfilment history for a request. |

---

## 8. Notifications  `/api/v1/notifications`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| GET | `/` | AUTH | My notifications (paged, filter status). |
| GET | `/unread-count` | AUTH | Badge count. |
| PATCH | `/{id}/read` | AUTH | Mark read. |
| PATCH | `/read-all` | AUTH | Mark all read. |

---

## 9. Admin  `/api/v1/admin`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| GET | `/dashboard` | ADMIN | KPIs (users, requests, fulfilment rate, stock alerts). |
| GET | `/users` | ADMIN | Manage users (filter role/status). |
| PATCH | `/users/{id}/enable` · `/disable` | ADMIN | Suspend/restore. |
| PATCH | `/hospitals/{id}/verify` | ADMIN | Approve hospital. |
| PATCH | `/blood-banks/{id}/verify` | ADMIN | Approve bank. |
| GET | `/audit-logs` | ADMIN | Audit trail (paged, filter). |

---

## 10. Reports  `/api/v1/reports`

| Method | Endpoint | Role | Purpose |
|--------|----------|------|---------|
| GET | `/requests/summary` | ADMIN/HOSPITAL | Counts by status/group/period. |
| GET | `/donations/summary` | ADMIN | Donations over time. |
| GET | `/inventory/summary` | ADMIN/BLOOD_BANK | Stock levels + expiry alerts. |
| GET | `/export?type=requests&format=csv|pdf` | ADMIN | Download report. |

---

## 11. Security applied to every endpoint
- JWT validated in `JwtAuthenticationFilter`; role checked via `@PreAuthorize`.
- Bean Validation (`@Valid`) on all request DTOs → 400 with `fieldErrors`.
- Rate limiting (Bucket4j) on `/auth/*` → 429 on abuse.
- CORS whitelist = frontend origin only. No entity ever returned — DTOs only.

**Next in M0:** repo scaffold (backend Maven + Spring Boot skeleton, frontend Vite skeleton, docker-compose), then wire M1 (Auth) code.
