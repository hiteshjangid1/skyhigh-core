# SkyHigh Core – Step-by-Step Development Tasks

> **Purpose:** Sequential task breakdown for end-to-end implementation.  
> **Order:** Tasks should be completed in sequence; dependencies are noted.

---

## Phase 0: Project Setup & Foundation

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 0.1 | **Initialize Spring Boot project** | Create Maven/Gradle project with Java 17, Spring Boot 3.x. Add dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, spring-boot-starter-cache, spring-boot-starter-amqp, springdoc-openapi. | — |
| 0.2 | **Configure application properties** | Set up `application.yml` for profiles (dev, test, prod). Placeholder config for DB, Redis, RabbitMQ. | 0.1 |
| 0.3 | **Create package structure** | Create packages: `domain`, `application`, `infrastructure`, `interfaces`. Sub-packages per module: seat, checkin, waitlist, baggage, payment, flight, abuse. | 0.1 |
| 0.4 | **Docker Compose skeleton** | Create `docker-compose.yml` with PostgreSQL, Redis, RabbitMQ. No app service yet. | — |

---

## Phase 1: Domain & Database

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 1.1 | **Flight & Seat domain** | Define `Flight` entity (id, flightNumber, departureTime, etc.). Define `Seat` entity (id, flightId, seatNumber, row, column). Define `SeatState` enum: AVAILABLE, HELD, CONFIRMED, CANCELLED. | 0.3 |
| 1.2 | **Seat reservation entity** | Define `SeatReservation` (id, seatId, passengerId, state, heldAt, heldUntil, confirmedAt). Links seat lifecycle to passenger. | 1.1 |
| 1.3 | **JPA repositories** | Create `FlightRepository`, `SeatRepository`, `SeatReservationRepository`. Add custom query for seats by flight with state. | 1.1, 1.2 |
| 1.4 | **Database migrations** | Use Flyway or Liquibase. Create initial migration for flights, seats, seat_reservations tables. Index on (flight_id, seat_number), (held_until) for expiry scan. | 1.2 |
| 1.5 | **Seed data script** | Create sample flight with seats for testing (optional, or use a bootstrap data loader). | 1.4 |

---

## Phase 2: Seat Lifecycle Core

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 2.1 | **Seat state machine** | Implement state transition logic in domain or service: AVAILABLE→HELD, HELD→CONFIRMED, HELD→AVAILABLE (expiry), CONFIRMED→CANCELLED→AVAILABLE. | 1.2 |
| 2.2 | **Hold seat service** | Implement `holdSeat(flightId, seatId, passengerId)`. Use `@Transactional` + `@Lock(PESSIMISTIC_WRITE)` on seat. Validate state=AVAILABLE, set HELD, set heldUntil = now + 120 seconds. Return reservation or throw if unavailable. | 1.3, 2.1 |
| 2.3 | **Confirm seat service** | Implement `confirmSeat(reservationId, passengerId)`. Lock reservation, validate ownership and state=HELD, check not expired. Set CONFIRMED, clear heldUntil. | 2.2 |
| 2.4 | **Release expired holds job** | Implement `@Scheduled` job (e.g., every 15s). Query reservations where state=HELD and heldUntil < NOW(). Update to AVAILABLE, delete or mark reservation. | 2.2, 1.3 |
| 2.5 | **Cancel seat service** | Implement `cancelSeat(reservationId, passengerId)`. Validate state=CONFIRMED, set CANCELLED, release seat to AVAILABLE. | 2.3 |
| 2.6 | **Conflict-free test** | Write integration test: concurrent hold attempts on same seat; only one succeeds. | 2.2 |

---

## Phase 3: REST API – Seat Operations

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 3.1 | **Seat controller – get seat map** | `GET /flights/{flightId}/seats` — return seat map with availability. No auth initially. | 1.3 |
| 3.2 | **Seat controller – hold** | `POST /flights/{flightId}/seats/{seatId}/hold` — body: passengerId. Return reservation details with heldUntil. | 2.2 |
| 3.3 | **Seat controller – confirm** | `POST /reservations/{reservationId}/confirm` — body: passengerId. | 2.3 |
| 3.4 | **Seat controller – cancel** | `POST /reservations/{reservationId}/cancel` — body: passengerId. | 2.5 |
| 3.5 | **DTOs and validation** | Create request/response DTOs. Use Bean Validation (@NotNull, etc.). | 3.1–3.4 |
| 3.6 | **Global exception handler** | `@ControllerAdvice` for SeatUnavailableException, InvalidStateException, etc. Return proper HTTP status and error body. | 2.2 |

---

## Phase 4: Seat Map Performance & Caching

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 4.1 | **Cache configuration** | Configure Spring Cache with Redis (or Caffeine). Define cache name e.g. `seatMap`. | 0.2 |
| 4.2 | **Cached seat map service** | Implement `getSeatMap(flightId)` with `@Cacheable("seatMap", key="#flightId")`. | 3.1 |
| 4.3 | **Cache invalidation** | On hold, confirm, cancel, expiry — add `@CacheEvict` or programmatic eviction for that flight’s cache. | 2.2, 2.3, 2.4, 2.5 |
| 4.4 | **Performance test** | Load test seat map endpoint (e.g., Gatling or JMeter). Target P95 < 1s. | 4.2 |

---

## Phase 5: Check-In Flow & Baggage

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 5.1 | **CheckIn entity** | Define `CheckIn` (id, flightId, passengerId, reservationId, status: IN_PROGRESS, AWAITING_PAYMENT, COMPLETED, CANCELLED). | 1.2 |
| 5.2 | **Baggage entity** | Define `BaggageRecord` (id, checkInId, weightKg, feeAmount, paid). | 5.1 |
| 5.3 | **Weight service client interface** | Define `WeightServiceClient.validateWeight(baggageId) → WeightResult`. Create stub implementation returning mock weight. | — |
| 5.4 | **Check-in service – start** | `startCheckIn(flightId, passengerId)` — hold seat (or use existing hold), create CheckIn IN_PROGRESS. | 2.2, 5.1 |
| 5.5 | **Check-in service – add baggage** | `addBaggage(checkInId, baggageId)`. Call Weight service. If > 25kg: set status AWAITING_PAYMENT, calculate fee, store BaggageRecord. If ≤ 25kg: continue. | 5.2, 5.3 |
| 5.6 | **Payment service client** | Define `PaymentServiceClient.processPayment(paymentRef, amount) → PaymentResult`. Create stub. | — |
| 5.7 | **Check-in service – complete payment** | `completePayment(checkInId, paymentRef)`. Verify payment, set baggage paid, if all baggage paid → confirm seat, set CheckIn COMPLETED. | 5.5, 5.6 |
| 5.8 | **Check-in REST API** | Endpoints: POST /checkin/start, POST /checkin/{id}/baggage, POST /checkin/{id}/payment. | 5.4, 5.5, 5.7 |

---

## Phase 6: Waitlist

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 6.1 | **WaitlistEntry entity** | Define `WaitlistEntry` (id, flightId, passengerId, createdAt, status: PENDING, ASSIGNED, EXPIRED). | 1.1 |
| 6.2 | **Join waitlist service** | `joinWaitlist(flightId, passengerId)` — create entry when seat unavailable. | 6.1 |
| 6.3 | **Seat released event** | Publish `SeatReleasedEvent` when: hold expires, cancellation. Use `ApplicationEventPublisher`. | 2.4, 2.5 |
| 6.4 | **Waitlist assignment listener** | On `SeatReleasedEvent`, find next PENDING waitlist entry for that flight. Attempt hold for that passenger. If success: assign, publish `WaitlistAssignedEvent`. If fail: keep seat AVAILABLE (or retry logic). | 6.2, 2.2 |
| 6.5 | **Notification stub** | On `WaitlistAssignedEvent`, log or call notification stub (email/push). Async with `@Async`. | 6.4 |
| 6.6 | **Waitlist REST API** | `POST /flights/{flightId}/waitlist` — join waitlist. `GET /waitlist/{passengerId}` — check status. | 6.2 |

---

## Phase 7: Abuse & Bot Detection

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 7.1 | **Access log model** | Track seat map access: (sourceId, flightId, timestamp). SourceId = IP or session/user. Store in DB or Redis. | — |
| 7.2 | **Access logging filter/interceptor** | Intercept GET /flights/*/seats. Log each access with source and timestamp. | 3.1 |
| 7.3 | **Abuse detection logic** | On each access, check: count distinct flightIds accessed by same source in last 2 seconds. If >= 50 → flag abuse. | 7.1 |
| 7.4 | **Abuse response** | On detection: return 429 Too Many Requests, optionally block source for N minutes (store in Redis). | 7.3 |
| 7.5 | **Audit logging** | Persist abuse events (source, timestamp, count) for audit. | 7.3 |

---

## Phase 8: Integration & Docker

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 8.1 | **Resilience4j setup** | Add circuit breaker for Weight and Payment clients. Configure retries and fallbacks. | 5.3, 5.6 |
| 8.2 | **OpenFeign clients** | Replace stub calls with Feign clients (or keep stubs for local dev). Configurable via profile. | 5.3, 5.6 |
| 8.3 | **Docker Compose – full stack** | Add skyhigh-core service to docker-compose. Ensure app starts after DB, Redis, RabbitMQ. Health checks. | 0.4 |
| 8.4 | **Environment configuration** | application-dev.yml (local), application-docker.yml (Docker). | 0.2 |

---

## Phase 9: Documentation & Quality

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 9.1 | **PRD.md** | Write Product Requirements Document: problem, goals, users, NFRs. | — |
| 9.2 | **README.md** | Project overview, prerequisites, setup (DB, env vars), run instructions, worker/job description. | All |
| 9.3 | **PROJECT_STRUCTURE.md** | Describe packages, modules, key classes. | 0.3 |
| 9.4 | **WORKFLOW_DESIGN.md** | Flow diagrams (seat lifecycle, check-in, waitlist). DB schema diagram. State history explanation. | 1.4, 2.1 |
| 9.5 | **ARCHITECTURE.md** | Finalize architecture doc (based on ARCHITECTURE_DESIGN.md). Include deployment diagram. | — |
| 9.6 | **API-SPECIFICATION.yml** | OpenAPI 3 spec for all endpoints. Or POSTMAN_COLLECTION.json. | 3.1–3.4, 5.8, 6.6 |
| 9.7 | **Unit tests** | Unit tests for services, domain logic. Target > 80% coverage on core modules. | 2.2, 2.3, 5.5 |
| 9.8 | **Integration tests** | Testcontainers for DB. Test full flows: hold→confirm, hold→expire, check-in with baggage. | 2.6, 5.8 |
| 9.9 | **Coverage report** | Configure JaCoCo. Generate report. Document in README. | 9.7 |
| 9.10 | **CHAT_HISTORY.md** | Chronicle design decisions and AI-assisted choices. | — |

---

## Phase 10: Final Validation

| # | Task | Description | Dependencies |
|---|------|-------------|--------------|
| 10.1 | **End-to-end smoke test** | Manual or automated: start via docker-compose, run through: view seat map → hold → confirm. Add baggage, payment, complete. | 8.3 |
| 10.2 | **Concurrent load test** | Simulate 100+ concurrent users on seat map and hold. Verify no duplicate assignments. | 2.6 |
| 10.3 | **Abuse scenario test** | Simulate 50 seat map accesses in 2s from one source. Verify 429 and audit log. | 7.4 |

---

## Task Dependency Graph (Simplified)

```
Phase 0 (Setup) ──► Phase 1 (Domain/DB) ──► Phase 2 (Seat Core) ──► Phase 3 (REST)
                                                                        │
Phase 4 (Cache) ◄────────────────────────────────────────────────────────┘
        │
        ▼
Phase 5 (Check-in/Baggage) ──► Phase 6 (Waitlist)
        │
        ▼
Phase 7 (Abuse) ──► Phase 8 (Integration) ──► Phase 9 (Docs) ──► Phase 10 (Validation)
```

---

## Suggested Sprint Mapping (Optional)

| Sprint | Phases | Focus |
|--------|--------|-------|
| 1 | 0, 1, 2 | Setup, domain, seat lifecycle |
| 2 | 3, 4 | REST API, caching |
| 3 | 5 | Check-in, baggage, payment |
| 4 | 6, 7 | Waitlist, abuse detection |
| 5 | 8, 9, 10 | Integration, docs, tests, validation |
