# Testing

## Unit Tests

Run all tests:

```bash
mvn test
```

Coverage report (JaCoCo): **`target/jacoco-report/index.html`** (open after `mvn test`). A summary is in **COVERAGE_REPORT.md**. The build enforces at least **80% line coverage** on the `com.skyhigh.application` service packages (seat, checkin, waitlist, abuse).

### By Area

| Area | Test Class | Notes |
|------|------------|--------|
| **Seat lifecycle** | `SeatServiceTest` | Hold, confirm, cancel, release expired holds. |
| **Hold-expiry edge cases** | `SeatServiceTest` | `confirmSeat_whenExpired_throws`, `confirmSeat_whenHeldUntilInPast_throwsHoldExpired`, `releaseExpiredHolds_releasesAndPublishesEvent`, `releaseExpiredHolds_doesNotReleaseFutureHolds`. |
| **Check-in** | `CheckInServiceTest` | Start check-in, add baggage, complete payment, get check-in. |
| **Waitlist** | `WaitlistServiceTest` | Join, get status, onSeatReleased assigns to next passenger. |
| **Abuse detection** | `AbuseDetectionServiceTest` | recordAccess, checkAbuse below/at/above threshold. |

### Concurrency and Hold-Expiry Tests

- **Hold-expiry:** `SeatServiceTest.confirmSeat_whenExpired_throws`, `confirmSeat_whenHeldUntilInPast_throwsHoldExpired`, `releaseExpiredHolds_releasesAndPublishesEvent`, `releaseExpiredHolds_doesNotReleaseFutureHolds` assert that expired holds are rejected (410) and that the scheduled job only releases past holds.
- **Concurrency (single-winner):** With **PostgreSQL** and pessimistic locking, concurrent hold attempts on the same seat would guarantee only one succeeds. With **H2** and `findById` (no lock), the integration test `SeatControllerIntegrationTest` in `src/test/java/com/skyhigh/interfaces/rest/SeatControllerIntegrationTest.java` runs seat map and hold flows; it does not assert concurrent single-winner (that would require a multi-threaded test or load test with PostgreSQL). See ARCHITECTURE_DESIGN.md §8 for locking behavior by environment.

Unit tests use **mocks** and **H2** where applicable; they do not prove cross-request concurrency. For that, run the integration test with PostgreSQL or a load test (see README / LOAD_TESTING.md).

## Integration Tests

- Integration tests that require PostgreSQL or Testcontainers may be disabled in the default `mvn test` run. See the test class for `@Disabled` and the comment explaining the requirement (e.g. "requires PostgreSQL for pessimistic locking").

## E2E (Postman)

Import `POSTMAN_COLLECTION.json` and run the collection (Collection Runner) in order. Prerequisite: start the app with `mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`. Base URL: `http://localhost:8080`.
