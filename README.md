# SkyHigh Core – Digital Check-In System

Backend service for SkyHigh Airlines self-check-in, handling seat management, baggage validation, waitlist, and abuse detection.

## Behavior and Features

- **Seat lifecycle:** Seats move AVAILABLE → HELD → CONFIRMED. A held seat is reserved for one passenger for **2 minutes**; if not confirmed in time, it is released automatically. Confirmed seats can be cancelled (seat becomes AVAILABLE or is offered to the waitlist).
- **Hold expiry:** A scheduled job runs every **15 seconds** and releases seats whose hold has passed `held_until`. Worst-case overrun is about 15 seconds after the 2-minute window.
- **Check-in flow:** Start check-in (holds a seat) → add baggage → if overweight (>25 kg), status becomes AWAITING_PAYMENT → complete payment → check-in COMPLETED and seat confirmed.
- **Waitlist:** When a seat is unavailable, passengers can join the waitlist. When a seat is released (expiry or cancellation), the next waitlisted passenger is assigned automatically (asynchronous). Clients should **poll** GET waitlist status to see when status becomes ASSIGNED.
- **Abuse / rate limiting:** GET seat map is protected. If a single source (e.g. IP) accesses **50 or more distinct flight seat maps within 2 seconds**, the server returns **429 Too Many Requests** with a **Retry-After** header (seconds until the window resets). See [Abuse detection](#abuse-detection) and `docs/ABUSE_AND_RATE_LIMITING.md`.
- **Error codes:** 409 Conflict = seat unavailable or conflict; 410 Gone = hold expired; 429 = rate limited. All errors use a consistent JSON body (timestamp, status, error, message, path). See `API-SPECIFICATION.yml`.

For architecture and design decisions, see `ARCHITECTURE_DESIGN.md` and `docs/ADR-001-architecture-decisions.md`. For database schema, see `SCHEMA.md`.

## Security

- **Current:** No authentication or authorization. APIs are unauthenticated; suitable for local dev and evaluation only.
- **Production:** Authentication (e.g. JWT or API key) and authorization (e.g. cancel only own reservation) should be added. Admin/audit endpoints should be restricted.
- **Input validation:** Request bodies are validated (Bean Validation); invalid input returns 400. Database access uses parameterized queries (no SQL injection). PII (e.g. passengerId) is stored; treat according to policy. Use HTTPS and secure secrets in production.

See **SECURITY.md** for details.

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for full stack)

## Quick Start (Local Development)

### Option 1: Standalone with H2 (no Docker)

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Windows PowerShell:** Use quotes so `-D` is passed correctly to Maven:
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Or use the batch script (Windows):** `run-dev.bat`

Uses in-memory H2 database. Redis and RabbitMQ are disabled.

### Option 2: With Docker (PostgreSQL, Redis, RabbitMQ)

```bash
docker compose up -d postgres redis rabbitmq
mvn spring-boot:run "-Dspring-boot.run.profiles=default"
```

**Windows PowerShell:**
```powershell
docker compose up -d postgres redis rabbitmq
mvn spring-boot:run "-Dspring-boot.run.profiles=default"
```

### Option 3: Full Docker Compose

```bash
docker compose up --build
```

Starts PostgreSQL, Redis, RabbitMQ, and the application. API available at http://localhost:8080.

## Database Setup

### PostgreSQL (Docker)

```bash
docker compose up -d postgres
```

Flyway runs automatically on startup and applies migrations from `src/main/resources/db/migration/`.

### H2 (Development)

No setup required. Flyway creates schema in-memory.

## Running the Application

```bash
mvn spring-boot:run
```

Default profile: `dev` (H2, no Redis/RabbitMQ). Override: `mvn spring-boot:run "-Dspring-boot.run.profiles=docker"` when using Docker infra.

## Background Workers

The hold-expiry job runs every 15 seconds (`@Scheduled`) to release seats held longer than 2 minutes. No separate worker process is needed.

## API Documentation

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

## Tests

```bash
mvn test
```

Unit tests run with H2. Integration test `SeatControllerIntegrationTest` runs with the test profile. Coverage report is generated at **`target/jacoco-report/index.html`**. See **COVERAGE_REPORT.md** for a summary and the enforced 80% line coverage on application service packages (seat, checkin, waitlist, abuse).

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| skyhigh.seat.hold-duration-seconds | 120 | Seat hold validity |
| skyhigh.baggage.max-weight-kg | 25 | Max baggage weight |
| skyhigh.abuse.threshold-count | 50 | Abuse: distinct flights in window |
| skyhigh.abuse.threshold-window-seconds | 2 | Abuse: time window |

## Abuse detection

Seat map requests (`GET /flights/{flightId}/seats`) are tracked per source (IP or `X-Forwarded-For`). If the same source accesses **50 or more distinct flight IDs** within **2 seconds** (configurable), the server responds with **429 Too Many Requests** and:

- **Retry-After** header: seconds after which the client may retry (typically the window length).
- **X-RateLimit-Limit** / **X-RateLimit-Remaining** (when applicable).
- JSON body: `{"error":"Too Many Requests","message":"..."}`.

Abuse events are stored for audit. Use **GET /admin/abuse/events** (optional query params: `sourceId`, `since`, `limit`) to list them. See `docs/ABUSE_AND_RATE_LIMITING.md` for full details.

## Load testing

Target NFR: seat map P95 &lt; 1 second. See **LOAD_TESTING.md** for approach, suggested tools (Gatling, k6, JMeter), and where to add scripts (`load-test/`).
