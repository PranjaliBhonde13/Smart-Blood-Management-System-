# Blood Bank Management System - Functional Analysis

This document explains the system from a user and workflow point of view. It focuses on who does what, who receives each request, who approves or rejects it, and how the request moves until it is completed.

## 1. Main Users

The system has four main operational users:

| User Type | Main Responsibility |
| --- | --- |
| Patient | Requests blood and tracks request status. |
| Donor | Maintains donor profile, views compatible approved requests, accepts or declines donation requests, and tracks donation history. |
| Hospital | Reviews patient blood requests, verifies them, approves/rejects them, searches blood sources, and completes donor-based donations. |
| Blood Bank | Maintains blood inventory and fulfills approved blood requests from available stock. |

Note: The codebase also has an Admin role for account verification, users, reports, and audit logs. This document focuses on the four operational users requested above.

## 2. Request Status Meaning

| Status | Simple Meaning | Who Usually Causes It |
| --- | --- | --- |
| PENDING | A patient has created the request. It is waiting for review. | Patient |
| VERIFIED | A hospital has checked the request and accepted it for processing. | Hospital |
| APPROVED | The request is approved for fulfillment by a donor or blood bank. | Hospital or Blood Bank |
| FULFILLED | A donor has accepted the request and is scheduled, or the request is being fulfilled. | Donor/Hospital/Blood Bank process |
| COMPLETED | Blood has been donated or issued and the request is closed successfully. | Hospital or Blood Bank |
| REJECTED | The request was not accepted for further processing. | Hospital or Blood Bank |
| CANCELLED | The patient cancelled the request before final completion. | Patient |

Basic lifecycle:

```text
Patient creates request
        |
        v
PENDING -> VERIFIED -> APPROVED -> FULFILLED -> COMPLETED
   |          |            |
   v          v            v
REJECTED   REJECTED     REJECTED

Patient can cancel while the request is still active.
```

## 3. User Functional Analysis

### 3.1 Patient

| Area | Details |
| --- | --- |
| Available functionalities | Register/login, complete patient profile, create blood request, upload medical report, view own requests, cancel active request, search nearby donors/blood banks. |
| Actions patient can perform | Select blood group, units required, urgency, location; submit request; upload report; track status; cancel request. |
| Requests patient can create | Blood request. |
| Who receives the request | Hospital sees it in the request queue. Blood Bank can also see request queue data, especially approved requests for fulfillment. |
| Who approves/rejects | Hospital primarily verifies and approves/rejects. Blood Bank can participate in approval/status handling and fulfillment. |
| After approval | Request becomes available for compatible donors and blood banks. Donors can accept it, or a blood bank can fulfill it from stock. |
| After rejection | Request is closed as REJECTED. Patient sees rejected status and receives a notification/update. |
| Information shown to patient | Blood group, units, urgency, request date, medical report status, current request status, notifications. |
| Other users involved | Hospital, Donor, Blood Bank. |

Patient flow:

```text
Patient -> Creates blood request -> Hospital reviews -> Approved/Rejected
If approved -> Donor accepts OR Blood Bank fulfills -> Request completed
If rejected -> Patient sees rejected status
```

### 3.2 Donor

| Area | Details |
| --- | --- |
| Available functionalities | Register/login, complete donor profile, set blood group/location/last donation date, set availability, view compatible approved requests, accept or decline requests, view donation history. |
| Actions donor can perform | Save profile, update location, mark availability, accept a request, decline a request, view scheduled/completed donations. |
| Requests donor can create | Donor does not create a blood request. When the donor accepts an approved request, the system creates a scheduled donation record. |
| Who receives donor action | Hospital receives donor acceptance/decline update. Patient is notified when a donor accepts. |
| Who approves/rejects | Donor decides whether to accept or decline an approved request. Hospital later marks the scheduled donation as completed. |
| After donor accepts | Donation is scheduled, request moves to FULFILLED, patient and hospital are notified. |
| After donor declines | Request remains open for other donors. Hospital is notified that this donor declined. |
| Information shown to donor | Compatible open requests, blood group needed, units, urgency, request date, donation status, donation history. |
| Other users involved | Patient and Hospital. Blood Bank may be involved only if it fulfills the same approved request from stock instead. |

Donor flow:

```text
Donor -> Views compatible approved requests -> Accepts or Declines
Accept -> Donation scheduled -> Hospital completes donation -> Request completed
Decline -> Request stays open for another donor/blood bank
```

### 3.3 Hospital

| Area | Details |
| --- | --- |
| Available functionalities | Register/login, complete hospital profile, view request queue, filter requests by status, verify patient requests, approve/reject requests, complete donor-based donations, search nearby donors and blood banks. |
| Actions hospital can perform | Review patient request, verify it, approve it, reject it, wait for donor, complete donation after fulfillment, search for blood sources. |
| Requests hospital can create | In the current system, Hospital does not create a separate hospital-owned blood request. Hospital manages patient-created requests. A hospital blood need should be handled through a patient request. |
| Who receives hospital action | Patient receives status updates. Donors see approved compatible requests. Blood banks can see approved requests for stock fulfillment. |
| Who approves/rejects | Hospital is the main reviewer and approver/rejecter of patient blood requests. |
| After approval | Request becomes visible to compatible donors and can be fulfilled by blood bank stock. |
| After rejection | Request is closed as REJECTED and patient is notified. |
| Information shown to hospital | Request number, blood group, units, urgency, status, request date, queue filters, action buttons such as Verify, Approve, Reject, Complete. |
| Other users involved | Patient, Donor, Blood Bank. |

Hospital flow:

```text
Hospital -> Opens request queue -> Reviews patient request
PENDING -> Verify -> VERIFIED
VERIFIED -> Approve or Reject
APPROVED -> Donor accepts or Blood Bank fulfills
FULFILLED -> Hospital marks complete
```

### 3.4 Blood Bank

| Area | Details |
| --- | --- |
| Available functionalities | Register/login, complete blood bank profile, manage blood stock, view inventory by blood group, update units and expiry dates, identify low stock, view approved requests, fulfill requests from stock. |
| Actions blood bank can perform | Add/update blood units, set expiry date, check low stock, fulfill approved request from stock. |
| Requests blood bank can create | Blood Bank does not create patient blood requests. It creates/updates inventory entries and can fulfill approved requests. |
| Who receives blood bank action | Patient receives fulfillment update. Hospital can see the request move forward/complete. |
| Who approves/rejects | Hospital is the main approval point. Blood Bank can participate in request status handling, but its main practical workflow is fulfillment from inventory. |
| After approval | Blood Bank can fulfill the approved request if it has enough matching stock. Stock units are reduced and the request is completed. |
| After rejection | Request is closed as REJECTED and cannot be fulfilled. |
| Information shown to blood bank | Stock by blood group, units available, expiry date, last updated date, low stock indicators, approved requests awaiting fulfillment, errors such as insufficient stock. |
| Other users involved | Patient and Hospital. Donor is involved only in donor-based fulfillment, not stock-based fulfillment. |

Blood Bank flow:

```text
Blood Bank -> Updates inventory -> Views approved requests
Approved request found -> Checks matching stock
Enough stock -> Fulfill from stock -> Units reduced -> Request completed
Not enough stock -> Fulfillment fails, request remains unresolved
```

## 4. Major Workflows

### 4.1 Patient Requesting Blood

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Patient | Creates request with blood group, units, urgency, and location. | Request becomes PENDING. |
| 2 | Hospital | Receives request in queue. | Hospital reviews details. |
| 3 | Hospital | Verifies request. | Request becomes VERIFIED. |
| 4 | Hospital/Blood Bank | Approves or rejects request. | Request becomes APPROVED or REJECTED. |
| 5A | Donor | Accepts approved compatible request. | Donation is scheduled and request becomes FULFILLED. |
| 5B | Blood Bank | Fulfills approved request from stock. | Stock is reduced and request is completed. |
| 6 | Hospital | Completes donor-based donation. | Request becomes COMPLETED. |
| Final | Patient | Views final status/notification. | Patient knows whether request was rejected, cancelled, or completed. |

Flow:

```text
Patient -> Create request -> Hospital queue -> Verify -> Approve/Reject
Approve -> Donor accepts OR Blood Bank fulfills -> Complete
Reject -> Patient notified and request closed
```

### 4.2 Donor Registering and Donating Blood

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Donor | Registers and completes donor profile. | Donor becomes available for matching. |
| 2 | Donor | Adds blood group, location, last donation date, and availability. | System can show compatible nearby requests. |
| 3 | Donor | Opens compatible approved requests. | Donor sees requests they can help with. |
| 4A | Donor | Accepts request. | Donation is scheduled; request becomes FULFILLED. |
| 4B | Donor | Declines request. | Hospital is notified; request stays open. |
| 5 | Hospital | Marks donation complete after donation is done. | Donation becomes COMPLETED; request becomes COMPLETED. |
| Final | Donor | Views donation history. | Donor sees scheduled/completed donations. |

Flow:

```text
Donor -> Completes profile -> Sees compatible approved requests
Accept -> Donation scheduled -> Hospital completes -> Donation history updated
Decline -> Request remains open for others
```

### 4.3 Hospital Requesting Blood

In the current system, Hospital does not create a separate hospital-owned blood request. The practical hospital workflow is to manage a patient-created request and arrange blood for that patient.

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Patient | Creates the blood request. | Request enters hospital queue as PENDING. |
| 2 | Hospital | Reviews and verifies request. | Request becomes VERIFIED. |
| 3 | Hospital | Approves request if valid. | Request becomes APPROVED. |
| 4 | Hospital | Searches nearby donors/blood banks if needed. | Hospital identifies possible sources. |
| 5 | Donor/Blood Bank | Donor accepts or blood bank fulfills from stock. | Request moves toward completion. |
| Final | Hospital | Completes donor-based donation or monitors bank fulfillment. | Patient request is completed. |

Flow:

```text
Hospital need for patient blood -> Patient request exists
Hospital verifies/approves -> Donor or Blood Bank supplies blood -> Request completed
```

### 4.4 Blood Bank Managing Blood Inventory

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Blood Bank | Opens inventory screen. | Current stock by blood group is shown. |
| 2 | Blood Bank | Adds or updates units and expiry date. | Inventory is saved. |
| 3 | System | Shows low stock indicators. | Blood Bank knows which groups need attention. |
| 4 | Patient/Hospital | Searches for blood nearby. | Blood bank appears if it has compatible stock. |
| Final | Blood Bank | Keeps inventory accurate. | Approved requests can be fulfilled from reliable stock. |

Flow:

```text
Blood Bank -> Update stock -> Inventory saved -> Low stock visible
Patients/Hospitals search -> Matching stock can be found
```

### 4.5 Blood Bank Approving/Rejecting Requests

The main practical Blood Bank screen handles approved requests and fulfillment. The request status system also allows Blood Bank participation in approval/rejection after review.

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Hospital | Verifies patient request. | Request becomes VERIFIED. |
| 2 | Blood Bank/Hospital | Reviews need and stock possibility. | Decision is made. |
| 3A | Blood Bank/Hospital | Approves request. | Request becomes APPROVED and can be fulfilled. |
| 3B | Blood Bank/Hospital | Rejects request. | Request becomes REJECTED. |
| 4A | Blood Bank | If approved and stock exists, fulfills from inventory. | Units are reduced and request completes. |
| 4B | Patient | If rejected, sees rejected status. | Process stops. |
| Final | Patient/Hospital/Blood Bank | Status is updated for everyone involved. | Request is either ready for fulfillment or closed. |

Flow:

```text
Verified request -> Blood Bank/Hospital reviews
Approve -> Fulfill from stock or donor route
Reject -> Patient notified, request closed
```

### 4.6 Hospital Managing Patient Blood Requests

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Hospital | Opens request queue. | Sees patient requests by status. |
| 2 | Hospital | Reviews PENDING request. | Can verify or reject. |
| 3 | Hospital | Verifies request. | Request becomes VERIFIED. |
| 4 | Hospital | Approves request. | Request becomes APPROVED. |
| 5 | Donor/Blood Bank | Fulfills approved request. | Request becomes FULFILLED or COMPLETED. |
| 6 | Hospital | Completes scheduled donor donation. | Request becomes COMPLETED. |
| Final | Patient | Receives status updates. | Patient can track outcome. |

Flow:

```text
Hospital -> Queue -> Verify -> Approve/Reject
Approve -> Wait for donor/bank -> Complete -> Patient updated
```

### 4.7 Donor Appointment/Donation Process

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Donor | Accepts an approved request. | Donation record is created as SCHEDULED. |
| 2 | System | Notifies patient and hospital. | Both know a donor has accepted. |
| 3 | Donor/Hospital | Donation is performed outside the system. | Blood is collected/arranged. |
| 4 | Hospital | Marks donation complete. | Donation status becomes COMPLETED. |
| 5 | System | Updates request status. | Request becomes COMPLETED. |
| Final | Donor/Patient/Hospital | Donation appears in history and request is closed. | Process is complete. |

Flow:

```text
Donor accepts -> Donation scheduled -> Patient/Hospital notified
Donation happens -> Hospital completes -> Request closed
```

### 4.8 Blood Collection and Inventory Update

| Step | Who | Action | Result |
| --- | --- | --- | --- |
| 1 | Blood Bank | Collects or receives blood units. | Blood is ready to be recorded. |
| 2 | Blood Bank | Adds/updates blood group units and expiry date. | Inventory increases or changes. |
| 3 | System | Shows updated stock. | Stock becomes searchable. |
| 4 | Patient/Hospital | Searches compatible blood banks. | Updated bank stock appears in results. |
| 5 | Blood Bank | Fulfills approved request when needed. | Inventory is reduced by issued units. |
| Final | Blood Bank | Sees latest available units. | Inventory remains current. |

Flow:

```text
Blood collected -> Blood Bank updates inventory -> Stock searchable
Approved request fulfilled -> Units reduced -> Inventory updated
```

### 4.9 Blood Request Fulfillment

There are two fulfillment paths.

#### Path A: Donor Fulfillment

```text
Patient request APPROVED
        |
        v
Compatible donor sees request
        |
        v
Donor accepts
        |
        v
Donation SCHEDULED + Request FULFILLED
        |
        v
Hospital marks donation complete
        |
        v
Request COMPLETED
```

| Who starts | Donor starts by accepting an approved request. |
| --- | --- |
| Who receives it | Hospital and Patient receive notification/update. |
| Who approves | Hospital/Blood Bank approval already happened before donor sees it. |
| What happens next | Donation is scheduled and later completed by Hospital. |
| Final result | Donation history is updated and patient request is completed. |

#### Path B: Blood Bank Stock Fulfillment

```text
Patient request APPROVED
        |
        v
Blood Bank sees approved request
        |
        v
Blood Bank checks matching stock
        |
        v
Enough stock? Yes -> Fulfill from stock
        |
        v
Inventory reduced + Request COMPLETED
```

| Who starts | Blood Bank starts by selecting an approved request to fulfill. |
| --- | --- |
| Who receives it | Patient receives fulfillment update; Hospital can see completed status. |
| Who approves | Request must already be approved before stock fulfillment. |
| What happens next | Units are deducted from inventory. |
| Final result | Request is completed from blood bank stock. |

## 5. Responsibility Summary

| Action | Started By | Received By | Approved/Rejected By | Final Result |
| --- | --- | --- | --- | --- |
| Create blood request | Patient | Hospital queue | Hospital/Blood Bank | Request becomes PENDING. |
| Verify blood request | Hospital | Patient/status system | Hospital | Request becomes VERIFIED. |
| Approve blood request | Hospital/Blood Bank | Patient, Donor feed, Blood Bank fulfillment queue | Hospital/Blood Bank | Request becomes APPROVED. |
| Reject blood request | Hospital/Blood Bank | Patient | Hospital/Blood Bank | Request becomes REJECTED and closes. |
| Cancel blood request | Patient | System/status list | Patient | Request becomes CANCELLED and closes. |
| Accept donation request | Donor | Hospital and Patient | Donor chooses to accept | Donation becomes SCHEDULED; request becomes FULFILLED. |
| Decline donation request | Donor | Hospital | Donor chooses to decline | Request remains open. |
| Complete donor donation | Hospital | Patient and Donor | Hospital | Request becomes COMPLETED. |
| Fulfill from stock | Blood Bank | Patient and Hospital/status system | Blood Bank action after request approval | Stock is reduced; request becomes COMPLETED. |
| Update inventory | Blood Bank | Search/matching system | Blood Bank | Stock is updated and visible for matching. |

## 6. What Each User Sees

| User | Main Status/Information Shown |
| --- | --- |
| Patient | Own request list, blood group, units, urgency, report attached/not attached, request date, current status, notifications. |
| Donor | Compatible approved requests, units needed, urgency, request date, accept/decline actions, donation history, scheduled/completed status. |
| Hospital | Request queue, filters by status, blood group, units, urgency, request date, actions to verify/approve/reject/complete. |
| Blood Bank | Inventory by blood group, units available, expiry date, low stock alerts, approved requests that can be fulfilled from stock. |

## 7. Simple End-to-End Example

```text
1. Patient creates request for O- blood, 2 units, critical urgency.
2. Request appears as PENDING in Hospital queue.
3. Hospital verifies the request.
4. Hospital approves the request.
5. Compatible donors can now see the request.
6. Blood Bank can also fulfill it if matching stock is available.
7A. If donor accepts: request becomes FULFILLED, then Hospital marks completed.
7B. If Blood Bank fulfills: inventory is reduced and request becomes COMPLETED.
8. Patient sees final status as COMPLETED.
```

## 8. Key Practical Rules

- Patient starts the blood request.
- Hospital is the main reviewer of patient requests.
- Request must be approved before donors or blood banks fulfill it.
- Donor acceptance schedules a donation; it does not immediately complete the request.
- Hospital completes donor-based donations.
- Blood Bank fulfillment immediately reduces stock and completes the request.
- Rejected, cancelled, and completed requests are final and do not move further.
- Inventory accuracy is important because patient/hospital searches depend on blood bank stock.
