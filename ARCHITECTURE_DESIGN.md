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

| Module | Responsibility | Key Entities / Concepts |
|--------|----------------|-------------------------|
| **seat** | Seat lifecycle, state machine, hold/confirm/cancel, conflict resolution | Seat, SeatState, SeatReservation |
| **checkin** | Orchestrates seat + baggage + payment flow; check-in status | CheckIn, CheckInStatus |
| **waitlist** | Queue management, assignment on availability, notifications | WaitlistEntry |
| **baggage** | Weight validation, fee calculation, integration with Weight service | BaggageRecord |
| **payment** | Payment initiation and verification, integration with Payment service | PaymentRecord |
| **flight** | Flight metadata, seat map structure | Flight, SeatMap |
| **abuse** | Rate limiting, bot/abuse detection, audit logging | AccessLog, AbuseEvent |

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

## 9. Document References

- **docs/ADR-001-architecture-decisions.md** — Architecture decision records (locking, expiry, monolith, events, abuse).
- **SCHEMA.md** — Database schema and seat state lifecycle.
- **WORKFLOW_DESIGN.md** — Flow diagrams, state history.
- **API-SPECIFICATION.yml** — API contracts.
- **PROJECT_STRUCTURE.md** — Package layout and module mapping.
