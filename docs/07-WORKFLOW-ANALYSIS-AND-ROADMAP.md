# Smart Blood Emergency Network — Workflow Analysis & Improvement Plan

**Purpose:** Explain how the 5 users connect today, what is broken or missing, and the target
workflow to build so the system behaves like a real, industry-grade blood network.

---

## 1. Who does what (one line each)

| Role | Real-world job in the network |
| --- | --- |
| **Admin** | Platform owner. Approves hospitals and blood banks (license check), manages users, monitors SLAs, reports, audit. |
| **Patient** | Raises a blood need (or a relative does), tracks it, gets notified. Never handles blood directly. |
| **Hospital** | Medical authority. Confirms the need is genuine (doctor/prescription), approves it, receives blood, confirms transfusion. **Owns the request.** |
| **Blood Bank** | Supplier. Holds inventory, reserves and issues units to the hospital, collects blood from donors (screening). |
| **Donor** | Volunteer supply. Gets alerted for compatible nearby needs, pledges, donates at the hospital or blood bank. |

**The key idea:** the *hospital* is the centre. Patients ask, hospitals validate, blood banks and donors supply,
the hospital confirms. Admin watches over everyone.

---

## 2. Current workflow (what the code does today)

```text
            (Admin verifies hospital/bank — but this flag is never checked anywhere)

Patient ──create──▶ PENDING ──(ANY hospital)verify──▶ VERIFIED ──approve──▶ APPROVED
                                                                      │
                     ┌────────────────────────────────────────────────┤
                     ▼                                                ▼
     Donor sees it in "Open Requests" (polling)        Blood bank sees it in "Requests"
     Donor "Accept"  → FULFILLED (1 donor = all units)  "Fulfill" → stock −units → COMPLETED
     Hospital "Complete" → COMPLETED                     (no hospital confirmation)
```

Relevant code: [BloodRequestService.java](../backend/src/main/java/com/sben/bloodrequest/BloodRequestService.java),
[DonationService.java](../backend/src/main/java/com/sben/donation/DonationService.java),
[RequestStatus.java](../backend/src/main/java/com/sben/bloodrequest/RequestStatus.java).

**What is already good:** clean package-by-feature backend, guarded status machine, JWT + refresh + OTP,
blood-compatibility matrix, geo radius search (bounding box + Haversine), in-app + email notifications,
audit log, Flyway migrations, Docker + CI, tests.

---

## 3. Gaps found in the existing flow

### 3.1 Workflow / business-logic gaps (fix first)

| # | Problem | Where | Real-world impact |
| --- | --- | --- | --- |
| G1 | Hospital/blood-bank `verified` flag is **never enforced** — unverified orgs can verify, approve, fulfil. | `AdminService` sets it, nothing reads it | Fake hospital can approve requests. Admin verification is meaningless. |
| G2 | Patient does not choose a hospital. **Any** hospital can claim **any** request; any hospital can approve/reject any request (no ownership check in `changeStatus`). | `BloodRequestService.verify/changeStatus` | Hospitals in another city can act on your request. |
| G3 | `BLOOD_BANK` can call `PATCH /status` → can approve/reject patient requests. | `BloodRequestController.updateStatus` | A supplier should never make medical decisions. |
| G4 | One donor "Accept" covers **all** units and moves the request to FULFILLED. | `DonationService.accept` | A donor gives 1 unit. A 4-unit request needs 4 donors / bank issue. |
| G5 | No donor eligibility check (90-day gap, age 18–65, availability) on accept; `lastDonationDate` not updated after donating. | `DonationService.accept/complete` | Unsafe donations, wrong future matching. |
| G6 | Blood bank fulfils only exact group (ignores compatible groups) and skips hospital receipt. Stock has one expiry date per group. No row lock → two banks/clicks can over-issue. | `DonationService.fulfillFromBank`, `blood_stock` | Expired blood issued, inventory goes wrong, no chain of custody. |
| G7 | Donors are **not alerted** when a matching request is approved — they must open the page. | no matching trigger | In an emergency, polling = lost minutes. This is the core "smart" feature. |
| G8 | Donor "decline" is only logged, not stored. No-show handling missing. | `DonationService.reject` | Cannot rank reliable donors, cannot re-broadcast. |
| G9 | No time dimension: no "required by", no expiry, no escalation for CRITICAL requests. | no scheduler | Requests sit forever; nobody is warned. |
| G10 | Request lacks real fields: blood **component** (whole blood / PRBC / platelets / plasma), patient name/age, admitting hospital, doctor, required-by time. | `blood_requests` table | Not usable by a real hospital. |
| G11 | Cancel by patient doesn't notify hospital/donor/bank; scheduled donation stays SCHEDULED. | `BloodRequestService.cancel` | Donor travels for a cancelled request. |
| G12 | Admin can only *approve* orgs — no reject-with-reason, no license document upload. | `AdminService` | Not a real KYC flow. |

### 3.2 Technical / industry-standard gaps

- Email is sent **synchronously inside the DB transaction** (`NotificationService.notify`) → slow requests; should be async/after-commit.
- Audit log only covers admin actions; request status changes and stock changes are not audited.
- No real-time push (WebSocket/SSE) and no SMS — emergency apps rely on these.
- No concurrency control (`@Version` optimistic lock) on request / stock rows.
- Medical reports stored on local disk; access rules for viewing reports need tightening (only the owner, the assigned hospital, admin).
- `README.md` contains a personal email address in the SMTP example — replace with a placeholder.

---

## 4. Target workflow (build towards this)

### 4.1 Onboarding

```text
All users ─register─▶ Email OTP ─▶ active
                                   │
     Patient / Donor ──────────────┴─▶ can use the app immediately
     Hospital / Blood Bank ─▶ fill profile + upload license ─▶ PENDING_REVIEW
                                   ─▶ Admin APPROVE ─▶ active (can act)
                                   ─▶ Admin REJECT (reason) ─▶ edit & resubmit
```
Rule: every hospital/blood-bank action checks `verified == true` (G1).

### 4.2 Emergency request lifecycle (main flow)

```text
1. RAISE      Patient (or Hospital on patient's behalf) creates request:
              blood group, component, units, urgency, required-by, admitting hospital,
              prescription upload                                   → PENDING
2. VALIDATE   Only the selected hospital reviews it                 → VERIFIED / REJECTED(reason)
3. APPROVE    Hospital approves                                     → APPROVED → SOURCING
4. MATCH      System automatically (Smart engine):
              a) finds verified blood banks nearby with compatible, non-expired stock
              b) alerts eligible nearby donors (push/SMS/email), radius grows with time
5. SUPPLY     (a) Blood bank: RESERVE units → DISPATCH → hospital confirms RECEIVED
              (b) Donor: PLEDGE 1 unit → arrives → screening → DONATED / DEFERRED
              Units collected add up → PARTIALLY_FULFILLED … → FULFILLED
6. CLOSE      Hospital confirms transfusion                         → COMPLETED
              Donor gets thanks + certificate, 90-day cooldown starts
   Anytime    Patient cancels → CANCELLED (everyone involved notified, reservations released)
   Timer      Past required-by with no supply → EXPIRED; CRITICAL not sourced in X min → escalate to Admin
```

### 4.3 Status machines

```text
Request:   PENDING → VERIFIED → APPROVED → SOURCING → PARTIALLY_FULFILLED → FULFILLED → COMPLETED
           (any active state) → REJECTED | CANCELLED | EXPIRED

Donor pledge:  PLEDGED → CHECKED_IN → DONATED
               PLEDGED → NO_SHOW | CANCELLED ;  CHECKED_IN → DEFERRED (failed screening)

Bank issue:    RESERVED → DISPATCHED → RECEIVED      (RESERVED → RELEASED on cancel/timeout)
```

### 4.4 Who can do what (permission matrix)

| Action | Patient | Donor | Hospital | Blood Bank | Admin |
| --- | :-: | :-: | :-: | :-: | :-: |
| Create request | ✅ own | | ✅ for patient | | |
| Verify / approve / reject request | | | ✅ **own requests, verified only** | | ✅ override |
| See request | own | compatible + nearby (no personal data) | own hospital's | approved, nearby | all |
| Pledge to donate | | ✅ if eligible | | | |
| Reserve / dispatch stock | | | | ✅ verified only | |
| Confirm received / transfused | | | ✅ | | |
| Mark donor donated / deferred | | | ✅ | ✅ | |
| Manage inventory | | | | ✅ own | view |
| Verify orgs, users, reports, audit | | | | | ✅ |

### 4.5 Interconnection (who notifies whom)

| Event | Patient | Donor | Hospital | Blood Bank | Admin |
| --- | :-: | :-: | :-: | :-: | :-: |
| Request created | ✔ | | ✔ | | |
| Verified / approved / rejected | ✔ | | | | |
| Approved (matching) | | ✔ nearby eligible | | ✔ nearby with stock | |
| Donor pledged / no-show | ✔ | | ✔ | | |
| Bank reserved / dispatched | ✔ | | ✔ | | |
| Completed | ✔ | ✔ thanks | | ✔ | |
| Cancelled / expired | ✔ | ✔ pledged | ✔ | ✔ reserved | |
| CRITICAL not sourced in time | | | ✔ | | ✔ |
| Stock low / near expiry | | | | ✔ | ✔ |

---

## 5. Improvement roadmap (in order)

### Phase 1 — Make the workflow correct (highest priority)
1. Enforce `verified` for hospital/bank actions (G1); add admin reject-with-reason + license upload (G12).
2. Add `hospital_id` selection at request creation; only that hospital can verify/approve/reject (G2).
3. Remove `BLOOD_BANK` from `PATCH /status`; give banks their own reserve/dispatch endpoints (G3).
4. Per-unit donor pledges; request fulfilled when collected units ≥ required (G4).
5. Donor eligibility service: age, 90-day gap, availability; update `lastDonationDate` on donation (G5).
6. Persist declines/no-shows; notify all parties on cancel and release reservations (G8, G11).

### Phase 2 — Make it "smart" and realistic
7. Auto-matching on APPROVED: notify nearby eligible donors + banks with stock; expanding radius by urgency (G7).
8. Scheduler (`@Scheduled`): expiry, CRITICAL escalation, low-stock & near-expiry alerts (G9).
9. Inventory by **batch** (`blood_units`: bag id, group, component, collected/expiry date, status) with FIFO issue of compatible groups and `@Version` locking (G6).
10. Richer request: component, required-by, patient details, doctor, ward (G10).
11. Request timeline page (every status change with who/when) for patient & hospital.
12. Donor features: eligibility countdown, donation certificate (PDF), badges, reliability score.

### Phase 3 — Industry standards
13. Async notifications (`@Async` / `@TransactionalEventListener(AFTER_COMMIT)`), SMS (e.g. Twilio/MSG91) and real-time WebSocket/SSE updates.
14. Audit every state change (request, pledge, stock); admin SLA dashboard (avg time to fulfil, by urgency/city).
15. Data privacy: donors never see patient identity/contact; reports visible only to owner + assigned hospital + admin; consent at signup.
16. Reliability: optimistic locking, idempotent endpoints, pagination everywhere, structured logs, Actuator metrics.
17. File storage on S3-compatible storage with signed URLs; virus/size/type checks.
18. Testing: integration tests for the full lifecycle (patient → hospital → bank/donor → complete) using Testcontainers MySQL.

---

## 6. Suggested new tables (Phase 1–2)

| Table | Key columns |
| --- | --- |
| `donation_pledges` (replace/extend `donation_history`) | request_id, donor_id, status, units, pledged_at, checked_in_at, deferral_reason |
| `blood_units` | bank_id, bag_no, blood_group, component, collected_on, expires_on, status (AVAILABLE/RESERVED/ISSUED/EXPIRED/DISCARDED) |
| `stock_issues` | request_id, bank_id, units, status (RESERVED/DISPATCHED/RECEIVED/RELEASED), timestamps |
| `request_status_history` | request_id, from_status, to_status, actor_user_id, reason, created_at |
| `org_documents` | owner type/id, file, status, reviewer, reject_reason |

Add `required_by`, `component`, `patient_name`, `doctor_name`, `rejection_reason`, `version` to `blood_requests`.
