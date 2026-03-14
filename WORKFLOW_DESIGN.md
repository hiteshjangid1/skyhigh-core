# Workflow Design

This document describes the main workflows: seat lifecycle, check-in, and waitlist. It complements **ARCHITECTURE_DESIGN.md** (concurrency, ADRs) and **API-SPECIFICATION.yml** (exact request/response schemas).

---

## Seat Lifecycle

### States and transitions

| State | Who can trigger | Next state(s) | Notes |
|-------|------------------|---------------|-------|
| **AVAILABLE** | — | HELD | Seat can be held by any passenger via POST hold. |
| **HELD** | Passenger (confirm), System (expiry), Passenger (cancel) | CONFIRMED, AVAILABLE | Only the holding passenger may confirm. Expiry: scheduled job every 15s. |
| **CONFIRMED** | Passenger (cancel) | AVAILABLE (or waitlist gets the seat) | Cancel via POST cancel; seat becomes AVAILABLE and SeatReleasedEvent is published. |

**Summary:** AVAILABLE → HELD → CONFIRMED. HELD can expire → AVAILABLE (job). CONFIRMED cancel → AVAILABLE (or waitlist assignment).

### Who triggers what

- **Hold:** Client `POST /seats/hold` (flightId, seatId, passengerId). SeatService.holdSeat.
- **Confirm:** Client `POST /seats/confirm` (reservationId, passengerId). SeatService.confirmSeat.
- **Cancel:** Client `POST /seats/cancel` (reservationId, passengerId). SeatService.cancelSeat.
- **Expiry:** Scheduled job `SeatService.releaseExpiredHolds()` every 15s; releases HELD where held_until < NOW(), publishes SeatReleasedEvent.

---

## Check-In Flow

### Steps and statuses

| Step | API | Status before → after | Notes |
|------|-----|------------------------|-------|
| 1. Start | POST /checkin/start (flightId, passengerId, seatId) | — → IN_PROGRESS | Holds seat; creates CheckIn and links reservation_id. |
| 2. Add baggage | POST /checkin/{checkInId}/baggage (passengerId, baggageId) | IN_PROGRESS → IN_PROGRESS or AWAITING_PAYMENT | If total weight ≤ 25 kg: stay IN_PROGRESS. If > 25 kg: AWAITING_PAYMENT. |
| 3. Payment | POST /checkin/{checkInId}/payment (passengerId, paymentRef) | AWAITING_PAYMENT → COMPLETED (when no unpaid baggage) | Confirms seat (reservation → CONFIRMED). |
| 4. Get status | GET /checkin/{checkInId}?passengerId=... | — | Returns current status and details. |

**Flow summary:** Start (hold seat) → Add baggage → If overweight: AWAITING_PAYMENT → Complete payment → Seat confirmed, status COMPLETED.

## Waitlist (event-driven design)

When a seat becomes **AVAILABLE** (hold expiry or cancellation), the system assigns it to the **next eligible waitlisted passenger** for that flight **asynchronously** via an in-process Spring event. Clients **poll** GET waitlist status; there is no synchronous “notify me” API.

### Sequence

1. **Seat release:** (a) **Hold expiry** — Scheduled job (every 15s) finds HELD reservations with held_until < NOW(), releases seat, publishes **SeatReleasedEvent(flightId, seatId)**. (b) **Cancel** — SeatService.cancelSeat sets seat to AVAILABLE and publishes same event.
2. **Event handling:** **WaitlistService.onSeatReleased(SeatReleasedEvent)** — @EventListener, @Async, @Transactional. Runs in a separate thread.
3. **Assignment:** Find oldest PENDING waitlist entry for that flight (FCFS). If none, return. If found, call SeatService.holdSeat(flightId, seatId, passengerId). On success: update entry to ASSIGNED, set reservationId and assignedAt, call **WaitlistNotificationSender.notifyAssigned**. On failure: catch, log; seat stays AVAILABLE, entry stays PENDING (no retry).
4. **Client visibility:** Poll **GET /flights/{flightId}/waitlist?passengerId=...** until status is ASSIGNED; then POST confirm with returned reservationId within hold window (2 min).

### Integration points

- **POST /flights/{flightId}/waitlist** — Body: passengerId. Join waitlist; returns entry with status PENDING.
- **GET /flights/{flightId}/waitlist?passengerId=...** — Returns PENDING or ASSIGNED; when ASSIGNED, reservationId and assignedAt present.
- **SeatReleasedEvent(flightId, seatId)** — Published by SeatService; consumed by WaitlistService.onSeatReleased.
- **WaitlistNotificationSender** — Interface notifyAssigned(entry, reservationId). Default: StubWaitlistNotificationSender (logs only). Replaceable for email/push.

### Waitlist notifications (extension point)

- **Default behavior:** **StubWaitlistNotificationSender** implements `WaitlistNotificationSender`. When a waitlist entry is assigned (status → ASSIGNED, reservationId set), `WaitlistService` calls `notificationSender.notifyAssigned(entry, reservationId)`. The stub **only logs** (e.g. INFO or DEBUG); no email, SMS, or push is sent. Clients discover assignment by **polling** GET waitlist status.
- **Replacing with real notifications:** Provide a different implementation of `WaitlistNotificationSender` (e.g. email sender, push notification service) and register it as the Spring bean. The interface receives the assigned `WaitlistEntry` (passengerId, flightId, reservationId) and `reservationId`; use these to send an email or push with a deep link to confirm. Keep the call non-blocking or offload to an async channel so the event listener remains fast.
- **Failure of notification:** If `notifyAssigned` throws, the assignment is already persisted (entry ASSIGNED, reservation HELD). The passenger can still see ASSIGNED via GET waitlist and confirm. Consider catching exceptions in the sender implementation and logging/retrying so one failing channel does not break the flow.

### Database (waitlist_entries)

id, flight_id, passenger_id, status (PENDING | ASSIGNED), reservation_id (set when ASSIGNED), created_at, assigned_at. FCFS order by created_at. One PENDING entry per (flight_id, passenger_id) enforced in application.

### Transaction and failure behaviour

- Seat release runs in SeatService transaction; event published after commit.
- onSeatReleased runs in its own transaction (async thread). If holdSeat fails, we do not update the entry (update only after success).
- **Hold fails:** Seat stays AVAILABLE; entry stays PENDING; next release may assign.
- **App restart after publish, before listener commit:** Event lost; seat already AVAILABLE; no assignment. Next release triggers flow again.
- **Duplicate join:** 400/409. **Flight not found:** 404.

### Client polling guidance

1. POST join with passengerId. 2. Poll GET status at reasonable interval (e.g. 5–15s). 3. When ASSIGNED, POST confirm with reservationId within 2 min. 4. Otherwise keep polling or timeout.

### Sequence (ASCII)

```
Client          POST /waitlist     GET /waitlist?passengerId=...   POST /seats/confirm
  |                     |                        |                            |
  |--- { passengerId } --------> Join (PENDING)  |                            |
  |<-------- 200 { status: PENDING } -------------|                            |
  |                     |                        |                            |
  |                     |   [Seat released]     |                            |
  |                     |   SeatService publishes SeatReleasedEvent            |
  |                     |   WaitlistService.onSeatReleased holds seat,        |
  |                     |   sets entry ASSIGNED, reservationId                |
  |                     |                        |                            |
  |                     |   Poll --------------->|                            |
  |                     |   <------- 200 { status: ASSIGNED, reservationId } -|
  |                     |                        |                            |
  |                     |                        |   POST confirm { reservationId } -->
  |                     |                        |   <-------- 200 ------------|
```

### Example request/response (waitlist)

- **POST /flights/1/waitlist** — Request: `{ "passengerId": "pax-001" }`. Response 200: `{ "id": 1, "flightId": 1, "passengerId": "pax-001", "status": "PENDING", "createdAt": "..." }`.
- **GET /flights/1/waitlist?passengerId=pax-001** — When PENDING: `{ "status": "PENDING", ... }`. When ASSIGNED: `{ "status": "ASSIGNED", "reservationId": 42, "assignedAt": "..." }`.

Full API contract: **API-SPECIFICATION.yml** under `/flights/{flightId}/waitlist`.

## Database Tables

| Table | Used in workflow |
|-------|-------------------|
| **flights** | Flight list; seat map is seats for a flight. |
| **seats** | Seat map (GET /flights/{id}/seats); state (AVAILABLE/HELD/CONFIRMED) drives hold/confirm. |
| **seat_reservations** | Hold creates HELD row with held_until; confirm sets CONFIRMED; cancel/expiry set CANCELLED. Waitlist assignment creates a new HELD reservation. |
| **check_ins** | One row per check-in session; status IN_PROGRESS, AWAITING_PAYMENT, COMPLETED, CANCELLED. Linked to reservation_id when seat is held. |
| **baggage_records** | Per check-in; weight_kg from Weight service; fee/paid drive AWAITING_PAYMENT → COMPLETED. |
| **waitlist_entries** | Join creates PENDING; onSeatReleased sets ASSIGNED and reservation_id. FCFS by created_at. |
| **access_logs** | One row per GET seat map request (source_id, flight_id, timestamp) for abuse detection. |
| **abuse_events** | One row when threshold exceeded (source, count, window, detected_at); queried via GET /admin/abuse/events. |
