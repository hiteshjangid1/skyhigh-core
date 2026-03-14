# SkyHigh Core – Digital Check-In System

Backend service for SkyHigh Airlines self-check-in, handling seat management, baggage validation, waitlist, and abuse detection. Built with **Java 17** and **Spring Boot 3.x** as a **modular monolith**; single deployable, one database (PostgreSQL or H2 for dev), with clear module boundaries (seat, check-in, waitlist, abuse, flight).

---

## Behavior and Features

### Seat lifecycle

- **States:** Each seat moves through **AVAILABLE** → **HELD** → **CONFIRMED**. A seat in **CANCELLED** state (after passenger cancellation) returns to AVAILABLE (or is offered to the waitlist).
- **AVAILABLE:** Seat can be viewed (seat map) and selected for hold. No reservation exists for this seat.
- **HELD:** Seat is temporarily reserved for exactly one passenger. A `SeatReservation` row exists with `state = HELD` and `held_until = held_at + 120 seconds` (configurable). Only the holding passenger may confirm; no other passenger can hold or confirm this seat.
- **CONFIRMED:** Seat is permanently assigned. The reservation remains CONFIRMED until the passenger cancels (before departure).
- **Conflict-free guarantee:** If multiple passengers attempt to hold the same AVAILABLE seat concurrently, only one succeeds (200); the other receives **409 Conflict** (seat unavailable or already held). Implementation uses database-level locking (PostgreSQL) or application checks (H2 dev); see ARCHITECTURE_DESIGN.md §8.

### Hold expiry

- **Duration:** Hold is valid for **120 seconds** (configurable via `skyhigh.seat.hold-duration-seconds`). After that, the seat must become AVAILABLE again so others can select it.
- **Mechanism:** A **scheduled job** runs every **15 seconds** (`@Scheduled` in SeatService). It queries `seat_reservations` where `state = 'HELD'` and `held_until < NOW()`, releases each (seat → AVAILABLE, reservation → CANCELLED), and publishes **SeatReleasedEvent** so the waitlist can assign the seat to the next passenger.
- **Worst case:** A hold that expires at time T may not be released until the next job run (T + up to 15s). So effective hold can be up to **2 min + 15s** in the worst case. This is documented in the architecture (ADR Decision 2).

### Check-in flow

1. **Start check-in:** Client calls `POST /checkin/start` with `flightId`, `passengerId`, `seatId`. The service holds the seat (if AVAILABLE) and creates a check-in with status **IN_PROGRESS**. Response includes `checkInId`.
2. **Add baggage:** Client calls `POST /checkin/{checkInId}/baggage` with `passengerId`, `baggageId`. The service calls the Weight service (stub in dev) to get weight. If **total baggage weight ≤ 25 kg** (configurable `skyhigh.baggage.max-weight-kg`), baggage is added and status stays IN_PROGRESS. If **total > 25 kg**, status becomes **AWAITING_PAYMENT** and the passenger must pay an extra baggage fee before continuing.
3. **Complete payment:** Client calls `POST /checkin/{checkInId}/payment` with `passengerId`, `paymentRef`. The service calls the Payment service (stub in dev). On success, unpaid baggage is marked paid; if no more unpaid baggage, check-in status becomes **COMPLETED** and the seat is confirmed (reservation → CONFIRMED). On payment failure, the service throws and returns 400/502.
4. **Get check-in:** `GET /checkin/{checkInId}?passengerId=...` returns current status (IN_PROGRESS, AWAITING_PAYMENT, COMPLETED, CANCELLED).

### Waitlist

- When a seat is **unavailable** (already HELD or CONFIRMED), a passenger can **join the waitlist** for that flight via `POST /flights/{flightId}/waitlist` (body: `passengerId`). One entry per passenger per flight; duplicate join returns 400.
- When a seat becomes **AVAILABLE** (hold expiry or cancellation), the system **asynchronously** assigns it to the **oldest PENDING** waitlist entry for that flight (first-come-first-served). Assignment is done via an in-process event: **SeatReleasedEvent** → **WaitlistService.onSeatReleased** → hold seat for that passenger and update entry to ASSIGNED.
- **Client visibility:** There is no push or webhook. Clients must **poll** `GET /flights/{flightId}/waitlist?passengerId=...`. When status is **ASSIGNED**, the response includes `reservationId`; the client must then call **POST confirm** with that reservation within the 2-minute hold window. See [Waitlist: event-driven architecture](#waitlist-event-driven-architecture-and-integration-points) and **WORKFLOW_DESIGN.md**.

### Abuse and rate limiting

- **GET /flights/{flightId}/seats** (seat map) is protected. If a single **source** (IP from X-Forwarded-For or Remote Address) accesses **50 or more distinct flight IDs** within **2 seconds** (rolling window), the server returns **429 Too Many Requests** with **Retry-After** (seconds) and **X-RateLimit-*** headers. Abuse incidents are stored and can be queried via **GET /admin/abuse/events**. See [Rate-limiting strategy](#rate-limiting-strategy-api-level-bot-detection) below.

### Error codes and API contract

- **400 Bad Request:** Invalid request body (e.g. missing or invalid `passengerId`), validation failure (Bean Validation), or business rule (e.g. wrong passenger for confirm).
- **404 Not Found:** Resource not found (e.g. flight, seat, reservation, check-in).
- **409 Conflict:** Seat unavailable or already held (hold/confirm); or duplicate waitlist join.
- **410 Gone:** Hold expired — client tried to confirm after `held_until` has passed.
- **429 Too Many Requests:** Rate limited (abuse detection on seat map).
- **500 Internal Server Error:** Unhandled exception (logged; message may be generic in production).
- All error responses use a consistent **ErrorResponse** schema: `timestamp`, `status`, `error`, `message`, `path` (optional `code`). Defined in **API-SPECIFICATION.yml** and returned by **GlobalExceptionHandler**.
- **Concurrency:** If two requests target the same AVAILABLE seat, one succeeds (200) and one receives 409 Conflict. If confirm is called after the hold has expired, the response is 410 Gone.
- **Implementation:** SeatService throws **SeatUnavailableException** (→ 409) when the seat is not available or already held, and **HoldExpiredException** (→ 410) when confirm is called after hold expiry. **GlobalExceptionHandler** maps these to HTTP 409 and 410 with ErrorResponse body.

**Advanced system behavior:** Waitlist event-driven flow, notification extension point (WaitlistNotificationSender), and client polling → **WORKFLOW_DESIGN.md** (Waitlist section, "Waitlist notifications (extension point)"). Abuse detection flow, 429 response, audit API, and operator operations → [Rate-limiting strategy](#rate-limiting-strategy-api-level-bot-detection) and [Abuse detection (detailed)](#abuse-detection-and-rate-limiting-detailed) below; runbook → **OBSERVABILITY.md**.

**Further reading:** Architecture and ADRs → **ARCHITECTURE_DESIGN.md**. Database schema → **SCHEMA.md**. Security and threat model → **SECURITY.md**. Testing and observability → **TESTING.md**, **OBSERVABILITY.md**. Design journey and **AI collaboration** (architectural reasoning, trade-off exploration) → **CHAT_HISTORY.md** (primary); **CHAT_HISTORY_FULL.md** (expanded). **Status of all seven evaluation areas** (AI collaboration, API docs, auth/security, observability, advanced workflows, testing, schema) → **docs/DOCUMENTATION_STATUS.md**.

## Security

- **Current:** No authentication or authorization. All APIs are unauthenticated; suitable for local dev and evaluation only. Anyone who can reach the service can call any endpoint (including admin abuse events).
- **Production:** Add authentication (e.g. JWT or API key) and authorization (e.g. cancel only own reservation, restrict GET /admin/abuse/events to admins). Protect secrets (DB password, API keys) via environment variables or a secret manager; use HTTPS and secure headers (e.g. HSTS, CSP where applicable).
- **Input validation:** Request bodies are validated with **Bean Validation** (e.g. `@NotNull`, `@NotBlank` on DTOs); invalid input returns **400** with a clear message. Path and query parameters are validated where applicable. Database access uses **parameterized queries** (JPA/Spring Data); no string concatenation into SQL, so **SQL injection** is mitigated.
- **PII:** Identifiers such as `passengerId` are stored; treat according to your data policy (retention, access control, encryption at rest if required). See **SECURITY.md** for threat model, validation details, and deployment guidance.

## Prerequisites

| Requirement | Version / notes |
|-------------|------------------|
| **Java** | 17 or later (LTS). Spring Boot 3.x requires Java 17+. Check with `java -version`. |
| **Maven** | 3.8 or later. Used for build, test, and run. Check with `mvn -v`. |
| **Docker & Docker Compose** | Required only for **Option 2** (run app locally against PostgreSQL/Redis/RabbitMQ) or **Option 3** (run entire stack in containers). Not required for Option 1 (H2 standalone). |
| **Git** | Optional; for cloning the repository. |

---

## Quick Start (Local Development)

### Option 1: Standalone with H2 (no Docker)

Best for **local development and evaluation**: no external services.

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Windows PowerShell:** The `-D` flag must be quoted so Maven receives it as one argument; otherwise PowerShell may treat it as a separate parameter and cause "Unknown lifecycle phase" errors:

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Windows (batch):** You can use the provided script: `run-dev.bat`.

- **Database:** In-memory **H2**. Schema is created by Flyway on startup from `src/main/resources/db/migration/`. No manual DB setup. Data is lost on restart.
- **Redis / RabbitMQ:** Not used in dev profile; no need to start them.
- **Port:** Application listens on **8080** (configurable via `server.port` in `application-dev.yml`).
- **Note:** With H2, pessimistic locking (`SELECT ... FOR UPDATE`) is not used; the app uses `findById`. Concurrency guarantees are best-effort in dev. For strict single-winner testing use PostgreSQL (Option 2 or 3).

### Option 2: With Docker (PostgreSQL, Redis, RabbitMQ)

Run the app on your host against containerized infrastructure.

1. **Start only the backing services:**
   ```bash
   docker compose up -d postgres redis rabbitmq
   ```
   Wait ~10–15 seconds for PostgreSQL to accept connections.

2. **Run the application** with the default (or docker) profile so it uses PostgreSQL and optional Redis:
   ```bash
   mvn spring-boot:run "-Dspring-boot.run.profiles=default"
   ```
   (On Windows PowerShell use the same quoted `-D` form.)

- **Database:** **PostgreSQL** (container). Flyway runs on app startup and applies migrations. Connection details are in `application.yml` / profile (e.g. `application-docker.yml` or default).
- **Redis / RabbitMQ:** Available for cache and messaging if the app is configured to use them (e.g. seat map cache, optional async publishing).

### Option 3: Full Docker Compose

Run the entire stack (database, Redis, RabbitMQ, and the application) in Docker.

```bash
docker compose up --build
```

- Builds the application image (Dockerfile) and starts all services defined in `docker-compose.yml`.
- API is available at **http://localhost:8080** once the app container is healthy (typically 1–2 minutes after startup).
- No local Java or Maven required for this option; useful for evaluators or CI.

---

## Database Setup

### PostgreSQL (Docker)

- Start the Postgres container: `docker compose up -d postgres`.
- **Flyway** runs automatically when the application starts. Migrations are in `src/main/resources/db/migration/` (e.g. `V1__create_flights_seats.sql`, `V2__...`). Version order is applied in sequence; do not modify or delete already-applied migrations.
- Connection URL, username, and password are set in the active profile (e.g. `spring.datasource.url=jdbc:postgresql://localhost:5432/skyhigh`, etc.). Override via environment variables if needed.

### H2 (Development)

- **No setup required.** When using profile `dev`, the application uses an in-memory H2 database. Flyway creates the schema on startup. No persistent data; restart clears everything. Useful for quick local runs and unit/integration tests.

---

## Running the Application

- **Command:** `mvn spring-boot:run` (uses default profile from `application.yml`; often `dev`).
- **Override profile:** `mvn spring-boot:run "-Dspring-boot.run.profiles=default"` or `"...=docker"` when using Docker infrastructure.
- **Stop:** `Ctrl+C` in the terminal. For Docker Compose: `docker compose down`.

---

## Background Workers

- **Hold-expiry job:** Implemented inside the application as a **scheduled task** (`@Scheduled`, fixed rate 15 seconds). Method: `SeatService.releaseExpiredHolds()`. It finds reservations with `state = HELD` and `held_until < NOW()`, releases each seat (seat state → AVAILABLE, reservation → CANCELLED), and publishes **SeatReleasedEvent** for waitlist assignment. No separate worker process or JAR; the same process that serves HTTP requests runs the job.
- **Waitlist assignment:** Triggered by **SeatReleasedEvent** (in-process). Handled asynchronously by **WaitlistService.onSeatReleased** (`@Async`). No cron or external queue required for the basic flow.

---

## API Documentation

- **Swagger UI (interactive):** After starting the app, open **http://localhost:8080/swagger-ui.html** in a browser. Allows exploring and invoking endpoints.
- **OpenAPI JSON:** **http://localhost:8080/v3/api-docs** — machine-readable spec (e.g. for code generation or import into Postman).
- **Contract file:** **API-SPECIFICATION.yml** in the repository root — authoritative API contract (paths, request/response schemas, error responses, 429 and rate-limit headers for seat map).

---

## Tests

- **Run all tests:** `mvn test`
- **What runs:** Unit tests (SeatServiceTest, CheckInServiceTest, WaitlistServiceTest, AbuseDetectionServiceTest, etc.) and integration tests (e.g. SeatControllerIntegrationTest). Tests use **H2** and mocks by default; no Docker required for `mvn test`.
- **Coverage:** JaCoCo report is generated at **`target/jacoco-report/index.html`**. Open in a browser after `mvn test`. CSV summary: `target/jacoco-report/jacoco.csv`. The build enforces **at least 80% line coverage** on the `com.skyhigh.application` packages (seat, checkin, waitlist, abuse); see **COVERAGE_REPORT.md** for a summary and instructions.
- **E2E (Postman):** Import **POSTMAN_COLLECTION.json**, start the app (`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`), set base URL to `http://localhost:8080`, and run the collection in order (flight/seat map → hold → confirm → check-in → waitlist → cancel → error cases).

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| **skyhigh.seat.hold-duration-seconds** | 120 | How long a seat hold is valid (seconds). Used to set `held_until = held_at + this value`. |
| **skyhigh.baggage.max-weight-kg** | 25 | Maximum allowed total baggage weight per check-in (kg). Above this, check-in status becomes AWAITING_PAYMENT until payment is completed. |
| **skyhigh.abuse.threshold-count** | 50 | Abuse detection: maximum number of **distinct flight IDs** a single source may access (GET seat map) within the time window. Exceeding returns 429. |
| **skyhigh.abuse.threshold-window-seconds** | 2 | Abuse detection: rolling time window (seconds). Distinct flight count is computed over the last N seconds per source. |
| **server.port** | 8080 | HTTP port. Override in profile or via `SERVER_PORT` environment variable. |
| **spring.profiles.active** | (profile-specific) | Set via `-Dspring-boot.run.profiles=dev` or `SPRING_PROFILES_ACTIVE`; controls which `application-<profile>.yml` is loaded. |

## Rate-limiting strategy (API level, bot detection)

The system **documents and exposes an explicit rate-limiting strategy at the API level** for bot detection.

| Item | Detail |
|------|--------|
| **Protected endpoint** | **GET /flights/{flightId}/seats** only. Other endpoints are not rate-limited by this mechanism. |
| **Source** | IP from **X-Forwarded-For** (first value) or **Remote Address**. No user/session identity. |
| **Threshold (default)** | **50 distinct flight IDs** in **2 seconds** (rolling window). Same flight requested multiple times counts as one distinct flight. |
| **Config** | `skyhigh.abuse.threshold-count` (default 50), `skyhigh.abuse.threshold-window-seconds` (default 2). |
| **Response when exceeded** | **429 Too Many Requests** with headers: **Retry-After** (seconds), **X-RateLimit-Limit**, **X-RateLimit-Remaining** (0); JSON body with `error`, `message`. |
| **Client guidance** | Wait at least **Retry-After** seconds before retrying; back off if 429 persists. |
| **Audit** | **GET /admin/abuse/events** — query params: `sourceId`, `since` (ISO-8601), `limit` (default 100, max 500). Returns list of recorded abuse events. |
| **Implementation** | `AbuseDetectionFilter` (runs before controller); `AbuseDetectionService` records access and checks count. |
| **OpenAPI** | `API-SPECIFICATION.yml`, path `/flights/{flightId}/seats`, response **429** and header definitions. |

### Abuse detection and rate limiting (detailed)

- **Overview:** SkyHigh Core detects abusive access patterns on seat map requests and returns 429 when a single source exceeds a configurable threshold within a time window. Each abuse incident is persisted for audit and can be queried via GET /admin/abuse/events.
- **Source identification:** If **X-Forwarded-For** is present, the first value is used; otherwise **Remote Address**. All requests from the same IP share the same source. Multiple users behind the same NAT share one source.
- **Threshold and rolling window:** Each GET seat map request is logged with sourceId, flightId, timestamp. We count **distinct flightIds** per source in the last `threshold-window-seconds` seconds. If count >= threshold, the current request gets 429. Same flight requested 50 times = 1 distinct; 50 different flights = 50 distinct → 429.
- **429 response:** Status 429, Content-Type application/json, headers **Retry-After** (seconds), **X-RateLimit-Limit**, **X-RateLimit-Remaining** (0). Body: JSON with error and message (escaped). Implementation: `AbuseDetectionFilter` (`com.skyhigh.interfaces.rest.filter`); on AbuseDetectedException sets status and headers and does not call the controller.
- **Audit API:** GET /admin/abuse/events. Query params: **sourceId** (filter by source), **since** (ISO-8601, events after this time), **limit** (default 100, max 500). Auth not implemented; production should restrict. Schema in API-SPECIFICATION.yml.
- **Implementation:** AbuseDetectionFilter (@Order(1)) checks path/method, derives sourceId, calls AbuseDetectionService.checkAbuse(sourceId). AbuseDetectionService records access in access_logs, counts distinct flight IDs in window, persists abuse_events and throws AbuseDetectedException when threshold exceeded. Config via @Value for threshold-count and threshold-window-seconds.
- **Metrics:** abuse_events_total counter incremented when abuse event is saved; exposed via Actuator. See OBSERVABILITY.md.
- **Operator operations (abuse detection):** When 429s or abuse spikes occur: (1) Query **GET /admin/abuse/events?since=...&limit=500** to list events. (2) Identify source (IP). (3) Decide whitelist vs block; tune threshold if needed. Full runbook: **OBSERVABILITY.md** (Runbook abuse, Runbook hold expiry).

## Waitlist: event-driven architecture and integration points

Waitlist assignment is implemented through a **clear event-driven architecture** with well-defined endpoints and integration points. Detailed data (sequence, DB columns, polling guidance, failure behaviour) is in **WORKFLOW_DESIGN.md** (Waitlist section).

| Item | Detail |
|------|--------|
| **Trigger** | Seat becomes AVAILABLE: (1) hold expiry (scheduled job every 15s), or (2) passenger cancels CONFIRMED reservation. |
| **Event** | **SeatReleasedEvent(flightId, seatId)** — published by SeatService after commit. In-process Spring event. |
| **Handler** | **WaitlistService.onSeatReleased** — `@EventListener`, `@Async`, `@Transactional`. Runs in a separate thread; finds oldest PENDING entry for that flight (FCFS), calls **SeatService.holdSeat**, on success sets entry to ASSIGNED and **reservationId**, then calls **WaitlistNotificationSender.notifyAssigned**. |
| **Notification** | Interface **WaitlistNotificationSender** (method `notifyAssigned(entry, reservationId)`). Default: **StubWaitlistNotificationSender** (logs only). Replaceable for email/push. |
| **POST /flights/{flightId}/waitlist** | Body: `{ "passengerId": "string" }`. Join waitlist; returns entry with **status PENDING**. One entry per passenger per flight. |
| **GET /flights/{flightId}/waitlist?passengerId=...** | Returns entry with **status** (PENDING or ASSIGNED). When ASSIGNED, **reservationId** and **assignedAt** present. Clients **poll** until ASSIGNED, then call POST confirm with that reservationId within the hold window (2 min). |
| **Failure** | If hold fails (e.g. seat taken), entry stays PENDING; no retry. Next seat release may assign. See WORKFLOW_DESIGN.md. |
| **OpenAPI** | **API-SPECIFICATION.yml** under `/flights/{flightId}/waitlist` — request/response schemas, “assignment is asynchronous; poll GET to see ASSIGNED”. |

## Load testing

Target NFR: seat map P95 &lt; 1 second. See **LOAD_TESTING.md** for approach, suggested tools (Gatling, k6, JMeter), and where to add scripts (`load-test/`).
