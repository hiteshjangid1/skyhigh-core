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

## 8. Document References

- **WORKFLOW_DESIGN.md** — Flow diagrams, DB schema, state history (to be created).
- **API-SPECIFICATION.yml** — API contracts (to be created).
- **PROJECT_STRUCTURE.md** — Package layout and module mapping (to be created).
