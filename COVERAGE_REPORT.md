# Test Coverage Report

Generated from JaCoCo after `mvn test`. For the latest HTML report, open **`target/jacoco-report/index.html`** in a browser (generate with `mvn clean test`).

---

## Summary (project-wide)

| Metric        | Covered | Total | Ratio  |
|---------------|---------|-------|--------|
| **Instructions** | 2,357 | 6,391 | **36%** |
| **Branches**      | 57   | 606   | **9%**  |
| **Lines**         | 378  | 602   | **63%** |
| **Methods**       | 244  | 589   | **41%** |
| **Classes**       | 51   | 65    | **78%** |

---

## Enforced packages (minimum 80% line coverage)

The build enforces **at least 80% line coverage** on these application packages:

| Package | Line coverage | Status |
|---------|----------------|--------|
| **com.skyhigh.application.seat**   | **96%** (78 covered / 81 total)  | Pass |
| **com.skyhigh.application.checkin**| **97%** (67 covered / 69 total)  | Pass |
| **com.skyhigh.application.waitlist** | **92%** (35 covered / 38 total) | Pass |
| **com.skyhigh.application.abuse**  | **100%** (24 covered / 24 total) | Pass |

---

## Service-level detail (from last run)

| Class | Lines covered | Lines total | Line % |
|-------|----------------|-------------|--------|
| SeatService | 78 | 81 | 96% |
| CheckInService | 67 | 69 | 97% |
| WaitlistService | 33 | 33 | 100% |
| StubWaitlistNotificationSender | 2 | 5 | 40% |
| AbuseDetectionService | 22 | 22 | 100% |
| AbuseDetectedException | 2 | 2 | 100% |

---

## How to generate the report

```bash
mvn clean test
```

- **HTML report:** `target/jacoco-report/index.html`
- **CSV (machine-readable):** `target/jacoco-report/jacoco.csv`

The JaCoCo check runs in the `test` phase and fails the build if any of the four application packages (seat, checkin, waitlist, abuse) fall below 80% line coverage.
