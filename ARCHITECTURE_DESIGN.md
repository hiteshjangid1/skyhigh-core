# SkyHigh Core – Architecture Design Document

> **Status:** Design Phase (Pre-Implementation)  
> **Stack:** Java 17+ | Spring Boot 3.x | Modular Monolith

---

## 1. Architectural Approach

### 1.1 Chosen Pattern: Modular Monolith with Domain-Driven Module Boundaries

We adopt a **Modular Monolith** rather than microservices for these reasons:

| Factor | Rationale |
|--------|-----------|
| **Strong consistency** | Seat assignment requires ACID guarantees. Cross-service transactions add complexity and weaken consistency. |
| **Conflict-free guarantee** | Single database + pessimistic locking provides the simplest, most reliable path to "only one wins" semantics. |
| **Operational simplicity** | Single deployable, simpler local development, no distributed tracing/debugging initially. |
| **Future extensibility** | Clear module boundaries allow later extraction to microservices if needed. |

### 1.2 High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           SKYHIGH CORE (Modular Monolith)                        │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │                     INTERFACE / ADAPTER LAYER                             │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │  │
│  │  │ REST API    │  │ Scheduled   │  │ Event       │  │ Abuse Detection │  │  │
│  │  │ Controllers │  │ Jobs        │  │ Listeners   │  │ Filter/Interceptor│  │  │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │  │
│  └─────────┼────────────────┼────────────────┼─────────────────┼────────────┘  │
│            │                │                │                 │               │
│  ┌─────────┼────────────────┼────────────────┼─────────────────┼────────────┐  │
│  │         ▼                ▼                ▼                 ▼            │  │
│  │                   APPLICATION / USE CASE LAYER                            │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │  │
│  │  │ Seat Mgmt   │  │ Check-in    │  │ Waitlist    │  │ Baggage &       │  │  │
│  │  │ Service     │  │ Service     │  │ Service     │  │ Payment Service │  │  │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘  │  │
│  └─────────┼────────────────┼────────────────┼─────────────────┼────────────┘  │
│            │                │                │                 │               │
│  ┌─────────┼────────────────┼────────────────┼─────────────────┼────────────┐  │
│  │         ▼                ▼                ▼                 ▼            │  │
│  │                        DOMAIN LAYER                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐    │  │
│  │  │  Seat (Entity, State Machine) | CheckIn | WaitlistEntry | etc.   │    │  │
│  │  └─────────────────────────────────────────────────────────────────┘    │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│            │                                                                   │
│  ┌─────────┼─────────────────────────────────────────────────────────────────┐  │
│  │         ▼                    INFRASTRUCTURE LAYER                          │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐   │  │
│  │  │ JPA Repos   │  │ Seat Map    │  │ Weight      │  │ Payment         │   │  │
│  │  │ (PostgreSQL)│  │ Cache       │  │ Client      │  │ Client          │   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────────┘   │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
            ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
            │  PostgreSQL  │  │  Redis       │  │  RabbitMQ    │
            │  (Primary DB)│  │  (Cache/Lock)│  │  (Async)     │
            └──────────────┘  └──────────────┘  └──────────────┘
```

---

## 2. Module Decomposition

| Module | Responsibility | Key Entities / Concepts | Key Classes (application / interfaces) |
|--------|----------------|-------------------------|----------------------------------------|
| **seat** | Seat lifecycle, state machine, hold/confirm/cancel, conflict resolution, hold-expiry job | Seat, SeatState, SeatReservation | **SeatService** (holdSeat, confirmSeat, cancelSeat, releaseExpiredHolds); SeatRepository, SeatReservationRepository; SeatController (REST). |
| **checkin** | Orchestrates seat + baggage + payment flow; check-in status (IN_PROGRESS, AWAITING_PAYMENT, COMPLETED, CANCELLED) | CheckIn, CheckInStatus | **CheckInService** (startCheckIn, addBaggage, completePayment, getCheckIn); CheckInController; WeightServiceClient, PaymentServiceClient (interfaces). |
| **waitlist** | Queue management, FCFS assignment on seat release, notifications | WaitlistEntry (PENDING, ASSIGNED) | **WaitlistService** (join, getStatus, onSeatReleased); WaitlistNotificationSender (interface); WaitlistController; StubWaitlistNotificationSender. |
| **baggage** | Weight validation, fee calculation, integration with Weight service | BaggageRecord | Logic embedded in CheckInService; BaggageRecord repository. |
| **payment** | Payment verification, integration with Payment service | PaymentRecord (if persisted) | PaymentServiceClient (interface); called from CheckInService.completePayment. |
| **flight** | Flight metadata, seat map structure (list of seats per flight) | Flight, SeatMap | FlightRepository; FlightController (flights list); seat map exposed via SeatController or FlightController (GET /flights/{id}/seats). |
| **abuse** | Rate limiting, bot/abuse detection, audit logging, 429 response | AccessLog, AbuseEvent | **AbuseDetectionService** (recordAccess, checkAbuse); **AbuseDetectionFilter** (servlet filter, @Order(1)); AbuseEventRepository; GET /admin/abuse/events. |

**Package mapping (see PROJECT_STRUCTURE.md):** `com.skyhigh.application.seat`, `com.skyhigh.application.checkin`, `com.skyhigh.application.waitlist`, `com.skyhigh.application.abuse`; domain entities in `com.skyhigh.domain`; REST in `com.skyhigh.interfaces.rest`; persistence in `com.skyhigh.infrastructure.persistence`.

---

## 3. Key Design Decisions

### 3.1 Concurrency Strategy: Pessimistic Locking

- **Mechanism:** `SELECT ... FOR UPDATE` (JPA `@Lock(PESSIMISTIC_WRITE)`) on seat row during hold/confirm.
- **Rationale:** Strong guarantee that only one transaction succeeds when multiple passengers target the same seat.
- **Trade-off:** Slight latency under contention, but correctness is mandatory.

### 3.2 Hold Expiry: Scheduled Job + DB Query

- **Mechanism:** `@Scheduled` job (e.g., every 10–15 seconds) finds holds with `held_until < NOW()` and releases them.
- **Alternative considered:** Redis TTL — rejected for initial design to avoid eventual consistency and dual source of truth.
- **Result:** Single source of truth in DB; job ensures timely release.

### 3.3 Seat Map Performance: Cache-Aside with Invalidation

- **Read path:** Check cache (Redis/Caffeine) for flight seat map; on miss, load from DB and populate cache.
- **Write path:** On any seat state change, invalidate cache for that flight.
- **Target:** P95 < 1s for seat map load.

### 3.4 External Services: Abstract Clients + Circuit Breaker

- **Weight Service** and **Payment Service** behind interfaces (`WeightServiceClient`, `PaymentServiceClient`).
- **Resilience4j** for circuit breaker and retries.
- **Stubs** for local/dev without real services.

### 3.5 Async Flows: Spring Events + Message Queue

- **In-process:** Spring `ApplicationEventPublisher` for seat-released → waitlist assignment.
- **Durable/Cross-process:** RabbitMQ for:
  - Hold expiry events (if job runs in separate worker)
  - Waitlist assignment notifications
  - Audit/abuse events
- **Rationale:** Spring Events for simplicity; RabbitMQ when durability or separate workers are needed.

---

## 4. Pros and Cons of This Approach

### 4.1 Pros

| # | Benefit |
|---|---------|
| 1 | **Strong consistency** — Single DB and pessimistic locking ensure no duplicate seat assignments. |
| 2 | **Simpler operations** — One deployable, one DB; easier debugging and local development. |
| 3 | **Clear boundaries** — Modules can be extracted into services later without a full rewrite. |
| 4 | **Transactional integrity** — Hold → Confirm flow in one transaction where needed. |
| 5 | **Mature ecosystem** — Spring Boot, Spring Data JPA, and standard patterns are well understood. |
| 6 | **Straightforward testing** — In-memory DB and mocks for external services. |

### 4.2 Cons

| # | Drawback | Mitigation |
|---|----------|------------|
| 1 | **Single point of scaling** — Cannot scale seat logic independently. | Vertical scaling first; horizontal scaling with DB read replicas for reads; later split modules if needed. |
| 2 | **DB as bottleneck** | Connection pooling, caching, and indexed queries; consider read replicas for seat map reads. |
| 3 | **Scheduled job delay** | Run job every 10–15s; worst-case hold overrun ~15s. Acceptable for 2-min window. |
| 4 | **Tight coupling in-process** | Module boundaries and interfaces reduce coupling; events help decouple. |
| 5 | **No polyglot** | All components in Java/Spring; acceptable for team expertise and consistency. |

---

## 5. Technology Stack Summary

| Concern | Technology |
|---------|------------|
| Runtime | Java 17+ |
| Framework | Spring Boot 3.x |
| Database | PostgreSQL 15+ |
| ORM | Spring Data JPA / Hibernate |
| Cache | Redis (or Caffeine for single-node) |
| Messaging | RabbitMQ |
| Build | Maven or Gradle |
| Testing | JUnit 5, Mockito, Testcontainers |
| API Docs | SpringDoc OpenAPI 3 |

---

## 6. Additional Tools for Decoupling, Concurrency, and Async Flows

### 6.1 Decoupling

| Tool | Purpose |
|------|---------|
| **Spring Cloud OpenFeign** | Declarative HTTP clients for Weight and Payment services. |
| **Resilience4j** | Circuit breaker, retry, rate limiter for external calls. |
| **Interface-based design** | `WeightServiceClient`, `PaymentServiceClient` as interfaces; swap implementations (real/stub). |
| **Spring Events** | In-process event-driven flows (e.g., seat released → waitlist). |
| **Configuration properties** | Externalized URLs and timeouts via `application.yml`. |

### 6.2 Concurrency

| Tool | Purpose |
|------|---------|
| **JPA Pessimistic Locking** | `@Lock(PESSIMISTIC_WRITE)` on seat operations. |
| **Transaction isolation** | Default READ_COMMITTED; consider SERIALIZABLE only for critical paths if needed. |
| **HikariCP** | Connection pool tuning for concurrent DB access. |
| **Redis distributed lock** | Optional, if scaling to multiple app instances and need cross-node coordination. |
| **@Async** | Non-blocking notifications or background tasks. |

### 6.3 Async Flows

| Tool | Purpose |
|------|---------|
| **Spring Events** | Seat released, check-in completed — in-process listeners. |
| **RabbitMQ** | Durable queues for hold expiry, waitlist assignment, notifications. |
| **@Scheduled** | Hold expiry scan, periodic cleanup. |
| **CompletableFuture / @Async** | Parallel calls to Weight + Payment when appropriate. |
| **Message-driven consumers** | `@RabbitListener` for async processing. |

### 6.4 Finalized Tool Matrix

| Category | Primary Tool | Secondary / Optional |
|----------|--------------|----------------------|
| HTTP Client | Spring Cloud OpenFeign | RestClient / WebClient |
| Resilience | Resilience4j | — |
| Caching | Redis | Caffeine (single-node) |
| Messaging | RabbitMQ | Spring Events (in-process) |
| Locking | DB pessimistic lock | Redis (multi-instance) |
| Scheduling | Spring @Scheduled | — |
| Async | Spring @Async, RabbitMQ | CompletableFuture |

---

## 7. Deployment View (Docker Compose)

```
┌─────────────────────────────────────────────────────────────────┐
│                     Docker Compose Stack                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   skyhigh-core-app (Spring Boot)                                │
│         │                                                       │
│         ├──► postgres:5432   (primary database)                 │
│         ├──► redis:6379      (cache, optional distributed lock) │
│         ├──► rabbitmq:5672   (async messaging)                  │
│         │                                                       │
│   weight-service (stub/mock - optional)                         │
│   payment-service (stub/mock - optional)                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Deployment notes:**
- **Application:** Single JAR (e.g. `skyhigh-core.jar`) run via `java -jar` or as Docker image. All REST, scheduled job, and event listeners run in the same process. No separate worker JAR.
- **Database:** PostgreSQL is the primary store. Flyway runs on startup; migrations from `src/main/resources/db/migration/`. Connection string, user, and password are profile-driven (e.g. `application-docker.yml` or env vars).
- **Redis:** Optional; used for seat map cache or distributed locking if enabled. If not configured, the app runs without Redis (e.g. dev profile).
- **RabbitMQ:** Optional; used for durable messaging if we add cross-process consumers. Current waitlist and hold-expiry use in-process events and @Scheduled.
- **Health:** Spring Boot Actuator exposes `/actuator/health` (DB, optional Redis). Use for readiness/liveness in Kubernetes or Docker Compose.
- **Scaling:** Multiple app instances can run behind a load balancer; they share the same PostgreSQL. Seat conflict is resolved by DB-level pessimistic locking. Hold-expiry job runs on every instance (idempotent).

---

## 8. Concurrency and Deployment

### 8.1 Single Instance vs Multiple Instances

- **Current design:** One application process, one database. All seat operations run in the same JVM; locking is at the DB row level (when using PostgreSQL).
- **Multi-instance:** If we run multiple app instances (e.g. behind a load balancer), they share the same database. Pessimistic locking (`SELECT ... FOR UPDATE`) is database-level, so only one transaction across all instances can hold the row lock for a given seat. Conflict-free seat assignment still holds. The hold-expiry job runs on every instance (every 15s); releasing an expired hold is idempotent (only one instance will update the row; others see the row already released).
- **Limitation:** No cross-instance distributed lock is used. For a single DB and same-datacenter instances, DB locking is sufficient. For multi-region or separate DBs per region, we would need a different strategy (e.g. single-writer or distributed lock).

### 8.2 Locking Behavior by Environment

| Environment | Database | Locking in code | Behavior |
|-------------|----------|-----------------|----------|
| **Dev (profile: dev)** | H2 in-memory | `findById` (no `FOR UPDATE`) | No row-level locking; suitable for local testing. Concurrent hold of same seat can race; H2 does not support `SELECT ... FOR UPDATE` in the same way as PostgreSQL. |
| **Production / Docker** | PostgreSQL | Can use `findByIdForUpdate` (pessimistic write) | Full conflict-free guarantee when repository methods use `@Lock(PESSIMISTIC_WRITE)`. Current implementation uses `findById` everywhere for compatibility with dev; to restore locking in prod, reintroduce `findByIdForUpdate` in SeatRepository/SeatReservationRepository and call it only when not using H2 (e.g. profile-based or dialect check). |

### 8.3 Edge Cases

- **Confirm at expiry boundary:** If a client calls confirm exactly when `held_until` has passed, the service checks `held_until.isBefore(Instant.now())` and returns 410 Gone (hold expired). The scheduled job may run shortly after and release the hold; ordering is "check in same request" then "job runs later."
- **Concurrent hold:** Two requests for the same AVAILABLE seat: with PostgreSQL and pessimistic locking, one transaction acquires the lock and succeeds (200); the other blocks then fails (seat no longer AVAILABLE) and returns 409 Conflict. With H2 and `findById`, both may read AVAILABLE; the first to save wins; the second may get 409 when the service re-checks state before save (or a duplicate if not guarded); concurrency testing in dev is best-effort.
- **Hold expiry job delay:** Worst case, a hold expires at T and the job runs at T+15s. The seat remains HELD for up to 15s after logical expiry. Acceptable for a 2-minute hold window.

---

## 9. Architecture Decision Records (ADR)

Key architecture decisions with alternatives considered, rationale, and consequences.

### Decision 1: Pessimistic Locking vs Optimistic Locking for Seat Assignment

**Status:** Accepted. **Context:** Multiple passengers may attempt to hold or confirm the same seat concurrently; we need at most one successful reservation per seat. **Decision:** Use pessimistic locking (`SELECT ... FOR UPDATE` / JPA `@Lock(PESSIMISTIC_WRITE)`) on the seat row during hold and confirm. **Alternatives:** Optimistic locking (version column) — no long-held locks but retries under contention, 409 + client retry. Redis distributed lock — decouples from DB but adds TTL/consistency edge cases. **Rationale:** Correctness is mandatory; pessimistic locking gives one winner without client retries. **Consequences:** With H2 (dev), FOR UPDATE not fully supported; we use findById in dev. With PostgreSQL, full locking available. Multi-instance with one DB still guarantees single winner via DB-level lock.

### Decision 2: Scheduled Job vs Redis TTL for Hold Expiry

**Status:** Accepted. **Context:** Seat holds valid 120 seconds; expired holds must be released. **Decision:** Scheduled job (Spring @Scheduled, every 15 seconds) that queries reservations with state = HELD and held_until < NOW() and releases them. **Alternatives:** Redis TTL — exact expiry but dual source of truth and callback failure risk. DB trigger — not portable. **Rationale:** Single source of truth in DB; 15s overrun acceptable for 2-min window. **Consequences:** Hold duration effectively up to 2 min + 15s worst case. Job runs on every instance; idempotent release logic.

### Decision 3: Modular Monolith vs Microservices

**Status:** Accepted. **Context:** Need check-in backend with seat, check-in, waitlist, abuse. **Decision:** Modular monolith: one application, one database, clear module boundaries. **Alternatives:** Microservices — independent scaling but distributed transactions and eventual consistency for hold-then-confirm. **Rationale:** Strong consistency required; single deploy and simpler ops. **Consequences:** All modules share one DB and process; observability is process-scoped.

### Decision 4: In-Process Events vs Message Queue for Waitlist Assignment

**Status:** Accepted (MVP). **Context:** When a seat is released, next waitlisted passenger should be assigned. **Decision:** Spring ApplicationEvent (SeatReleasedEvent) and @EventListener in WaitlistService with @Async. No RabbitMQ for this flow initially. **Alternatives:** RabbitMQ — durable, multi-instance safe but extra component. **Rationale:** MVP and single-instance; in-process sufficient; waitlist assignment best-effort. **Consequences:** Event not durable; clients poll GET waitlist status. See WORKFLOW_DESIGN.md for waitlist flow.

### Decision 5: Abuse Detection — Application-Level vs Gateway Rate Limiting

**Status:** Accepted. **Context:** Detect and block abusive patterns (e.g. 50+ distinct seat maps in 2s from one source). **Decision:** Implement abuse detection inside the application (filter + service + DB). Return 429 with Retry-After and X-RateLimit-* headers. **Alternatives:** Gateway rate limiting — offloads work but less flexible for “distinct flight count in window”; audit may live outside app. **Rationale:** Distinct flight count in time window easier in app; we persist abuse events and expose GET /admin/abuse/events. **Consequences:** 429 includes Retry-After; audit endpoint for operators (auth TBD). See README “Rate-limiting strategy” section.

---

## 10. Document References

- **SCHEMA.md** — Database schema and seat state lifecycle.
- **WORKFLOW_DESIGN.md** — Flow diagrams, seat lifecycle, check-in flow, waitlist event-driven design.
- **README.md** — Rate-limiting strategy and abuse detection (detailed).
- **API-SPECIFICATION.yml** — API contracts.
- **PROJECT_STRUCTURE.md** — Package layout and module mapping.
