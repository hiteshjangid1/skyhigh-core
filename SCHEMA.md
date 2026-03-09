# Database Schema and State Lifecycle

This document describes the database tables, columns, constraints, indexes, and the semantics of seat state and hold management. See migrations in `src/main/resources/db/migration/`.

---

## Tables Overview

| Table | Purpose |
|-------|---------|
| `flights` | Flight metadata (number, origin, destination, departure, total seats). |
| `seats` | Per-flight seats; `state` drives availability (AVAILABLE, HELD, CONFIRMED). |
| `seat_reservations` | Links passenger to seat; holds have `held_until`; lifecycle state. |
| `check_ins` | Check-in session (flight, passenger, reservation, status). |
| `baggage_records` | Baggage per check-in (weight, fee, paid). |
| `waitlist_entries` | Passengers waiting for a seat on a flight; status PENDING/ASSIGNED. |
| `access_logs` | Seat-map access audit (source_id, flight_id, timestamp). |
| `abuse_events` | Recorded abuse incidents (source, count, window, detected_at). |

---

## Flights

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK, identity. |
| flight_number | VARCHAR(50) | NOT NULL, UNIQUE. |
| origin, destination | VARCHAR(100) | NOT NULL. |
| departure_time | TIMESTAMP | NOT NULL. |
| total_seats | INTEGER | NOT NULL. |
| created_at, updated_at | TIMESTAMP | |

---

## Seats

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK, identity. |
| flight_id | BIGINT | NOT NULL, FK → flights(id). |
| seat_number | VARCHAR(10) | NOT NULL. |
| row_number | INTEGER | NOT NULL. |
| column_letter | VARCHAR(5) | NOT NULL. |
| state | VARCHAR(20) | NOT NULL, DEFAULT 'AVAILABLE'. Allowed: AVAILABLE, HELD, CONFIRMED. |
| version | BIGINT | Default 0 (for optional optimistic locking). |

**Constraints:**
- `uk_seat_flight_number` UNIQUE (flight_id, seat_number).
- `fk_seat_flight` FOREIGN KEY (flight_id) REFERENCES flights(id).

**Indexes:** idx_seat_flight, idx_seat_flight_number.

**State semantics:**
- **AVAILABLE:** Can be held by a passenger.
- **HELD:** Temporarily reserved; a row in `seat_reservations` exists with state HELD and `held_until` in the future.
- **CONFIRMED:** Permanently assigned; reservation is CONFIRMED.

State is updated by application logic (hold, confirm, cancel, expiry job). There are no CHECK constraints on `state` in the current migrations; see "Data integrity" section.

---

## Seat Reservations

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK, identity. |
| seat_id | BIGINT | NOT NULL, FK → seats(id). |
| flight_id | BIGINT | NOT NULL, FK → flights(id). |
| passenger_id | VARCHAR(100) | NOT NULL. |
| state | VARCHAR(20) | NOT NULL. Allowed: HELD, CONFIRMED, CANCELLED. |
| held_at | TIMESTAMP | When the hold started. |
| held_until | TIMESTAMP | When the hold expires; NULL when CONFIRMED or CANCELLED. |
| confirmed_at | TIMESTAMP | Set when state becomes CONFIRMED. |
| cancelled_at | TIMESTAMP | Set when state becomes CANCELLED. |
| created_at, updated_at | TIMESTAMP | |

**Constraints:**
- `fk_reservation_seat`, `fk_reservation_flight` FOREIGN KEYs.

**Indexes:** idx_reservation_seat, idx_reservation_held_until, idx_reservation_passenger.

**Hold management:**
- **held_until:** For HELD reservations, this is `held_at + hold_duration_seconds` (configurable, default 120). The scheduled job selects rows with `state = 'HELD'` and `held_until < NOW()` and releases them (seat → AVAILABLE, reservation → CANCELLED).
- **State lifecycle:** HELD → CONFIRMED (passenger confirms in time); HELD → CANCELLED (expiry or cancel before confirm); CONFIRMED → CANCELLED (passenger cancels).

Integrity (one active reservation per seat, valid state transitions) is enforced in application code. Database-level CHECK constraints for state values are optional (see FEEDBACK_ANALYSIS_AND_TASKS.md Theme I).

---

## Check-Ins

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK, identity. |
| flight_id | BIGINT | NOT NULL, FK → flights(id). |
| passenger_id | VARCHAR(100) | NOT NULL. |
| reservation_id | BIGINT | Optional; links to seat_reservations when check-in holds a seat. |
| status | VARCHAR(30) | NOT NULL. IN_PROGRESS, AWAITING_PAYMENT, COMPLETED, CANCELLED. |
| created_at, updated_at, completed_at | TIMESTAMP | |

**Indexes:** idx_checkin_passenger, idx_checkin_flight.

---

## Baggage Records

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK, identity. |
| check_in_id | BIGINT | NOT NULL, FK → check_ins(id). |
| baggage_id | VARCHAR(100) | NOT NULL. |
| weight_kg | DECIMAL(10,2) | NOT NULL. |
| fee_amount | DECIMAL(10,2) | Set when overweight; paid via payment flow. |
| paid | BOOLEAN | NOT NULL, DEFAULT FALSE. |
| created_at | TIMESTAMP | |

**Index:** idx_baggage_checkin.

---

## Waitlist Entries

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK, identity. |
| flight_id | BIGINT | NOT NULL, FK → flights(id). |
| passenger_id | VARCHAR(100) | NOT NULL. |
| status | VARCHAR(20) | NOT NULL. PENDING, ASSIGNED. |
| reservation_id | BIGINT | Set when ASSIGNED (link to seat_reservations). |
| created_at, assigned_at | TIMESTAMP | |

**Indexes:** idx_waitlist_flight_status, idx_waitlist_created.

---

## Access Logs and Abuse Events

**access_logs:** source_id, flight_id, accessed_at, endpoint. Used to count distinct flights per source in a time window for abuse detection.

**abuse_events:** source_id, access_count, window_seconds, detected_at. One row per detected abuse incident; can be queried via audit API.

---

## Data Integrity (Application vs Database)

- **Foreign keys** are enforced at the DB level (flight_id, seat_id, etc.).
- **Uniqueness** of (flight_id, seat_number) is enforced at the DB level.
- **State values** and **valid transitions** (e.g. only HELD can become CONFIRMED; only CONFIRMED can be cancelled) are enforced in **application code** (SeatService, CheckInService). There are no CHECK constraints on `seats.state` or `seat_reservations.state` in the current migrations.
- **Rationale:** Allows flexibility (e.g. adding a new state or transition) without a migration; application is the single writer. For stricter guarantees, a future migration could add CHECK constraints (e.g. `state IN ('AVAILABLE','HELD','CONFIRMED')` for seats).
