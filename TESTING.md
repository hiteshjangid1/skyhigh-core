# Testing

## Running Tests

**Full suite:**
```bash
mvn test
```

**By package (Maven Surefire):** Run only tests in a given package, e.g.:
```bash
mvn test -Dtest=com.skyhigh.application.seat.**.*Test
mvn test -Dtest=*SeatServiceTest
```

**By tag (JUnit 5):** If tests are tagged (e.g. `@Tag("integration")`), run only unit tests by excluding integration: `mvn test -Dgroups=unit` (if groups are configured in surefire). Check `pom.xml` for `groups` / `excludedGroups` configuration.

Tests run with the **test** profile by default; they use **H2** and mocks. No Docker or external services required for `mvn test`.

---

## Unit Tests

### Coverage

- **Report (JaCoCo):** Generated at **`target/jacoco-report/index.html`**. Open in a browser after `mvn test`. CSV: `target/jacoco-report/jacoco.csv`.
- **Interpretation:** Line coverage shows which lines were executed. The build enforces **at least 80% line coverage** on `com.skyhigh.application` packages (seat, checkin, waitlist, abuse). See **COVERAGE_REPORT.md** for a summary and how to fix coverage gaps.
- **Exclusions:** Often test code, main method, DTOs, and config are excluded from coverage; check `pom.xml` JaCoCo configuration.

### Test Classes and What They Cover

| Test Class | Package / path | Coverage |
|------------|----------------|----------|
| **SeatServiceTest** | application/seat | Hold, confirm, cancel, releaseExpiredHolds; expired hold returns exception (410); job releases only past holds. |
| **CheckInServiceTest** | application/checkin | startCheckIn, addBaggage (under/over weight), completePayment, getCheckIn; status transitions IN_PROGRESS, AWAITING_PAYMENT, COMPLETED. |
| **WaitlistServiceTest** | application/waitlist | join, getStatus; onSeatReleased assigns to oldest PENDING; duplicate join, not found. |
| **AbuseDetectionServiceTest** | application/abuse | recordAccess, checkAbuse below/at/above threshold; 429 when threshold exceeded. |
| **SeatControllerIntegrationTest** | interfaces/rest | Full HTTP: GET seat map, POST hold, POST confirm, POST cancel; uses embedded H2 and real controllers. |

### Edge cases and concurrency — what is demonstrated

| Scenario | Demonstrated by | Notes |
|----------|-----------------|-------|
| **Hold expiry (client confirms after expiry)** | `SeatServiceTest.confirmSeat_whenExpired_throws`, `confirmSeat_whenHeldUntilInPast_throwsHoldExpired` | Assert that confirm throws (HoldExpiredException → 410 Gone). |
| **Hold expiry (scheduled job)** | `SeatServiceTest.releaseExpiredHolds_releasesAndPublishesEvent`, `releaseExpiredHolds_doesNotReleaseFutureHolds` | Job releases only reservations with `held_until` in the past; publishes SeatReleasedEvent; does not release future holds. |
| **Seat conflict (409)** | Unit tests for hold/confirm when seat is unavailable; integration test hold/confirm flows | 409 when seat already HELD/CONFIRMED. |
| **Concurrent hold (single-winner)** | **Not demonstrated** by unit or integration tests | H2 does not provide pessimistic locking. To validate single-winner under concurrency: run with **PostgreSQL** (README Option 2/3) and use a **load test** (LOAD_TESTING.md) or multi-threaded test that asserts at most one reservation per seat. See ARCHITECTURE_DESIGN.md §8. |
| **Waitlist assignment on seat release** | `WaitlistServiceTest` (onSeatReleased assigns to oldest PENDING) | Event-driven assignment and notification hook are covered. |
| **Abuse detection (429)** | `AbuseDetectionServiceTest` (threshold, recordAccess, checkAbuse) | Filter and 429 response are tested at service level; integration test can hit 429 with many distinct flight IDs. |

**Summary:** Hold expiry behavior and 409/410 semantics are clearly covered by named unit tests. Concurrency (single-winner) is intentionally not asserted in the default test suite; use PostgreSQL and load or concurrent tests to demonstrate it.

**Implementation (409/410):** SeatService throws **SeatUnavailableException** (→ 409) and **HoldExpiredException** (→ 410); GlobalExceptionHandler maps these to HTTP 409 and 410 with ErrorResponse (see API-SPECIFICATION.yml). The Postman collection includes error cases for hold unavailable (409) and expired hold (410).

---

## Integration Tests

- **SeatControllerIntegrationTest** runs against embedded H2 and full Spring context (controllers, services, repositories). No Testcontainers by default.
- Tests that require **PostgreSQL** or **Testcontainers** may be **@Disabled** with a comment (e.g. "requires PostgreSQL for pessimistic locking"). Enable and run with the appropriate profile or environment when validating concurrency.

---

## E2E (Postman)

1. **Prerequisite:** Start the app: `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`.
2. **Import:** Open Postman → Import → select **POSTMAN_COLLECTION.json** from the project root.
3. **Variables:** Set collection or environment variable **baseUrl** = `http://localhost:8080` (or your host/port).
4. **Run order:** Use Collection Runner and run the collection in order: e.g. list flights → get seat map → hold seat → confirm → check-in start → add baggage → (optional) payment → waitlist join → get waitlist status → cancel → error cases (expired hold, 409, 429).
5. **Verify:** Check status codes (200, 201, 400, 404, 409, 410, 429) and response bodies against API-SPECIFICATION.yml.
