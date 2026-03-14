# Load Testing

## Goal

- **Seat map (GET /flights/{id}/seats):** **P95 latency &lt; 1 second** under load (NFR from PRD).
- **Concurrent seat holds:** **No duplicate seat assignments** when many users hold/confirm the same seat (single-winner guarantee).

## Tool options

| Tool | Pros | Script location |
|------|------|-----------------|
| **Gatling** (Scala/Java) | Good reports, DSL, Maven plugin | `load-test/gatling/` (e.g. `src/test/scala` or Java simulations) |
| **k6** (JavaScript) | Simple scripts, CI-friendly | `load-test/k6/` (e.g. `seat-map.js`, `hold-confirm.js`) |
| **JMeter** | GUI, many users familiar | `load-test/jmeter/` (e.g. `seat-map.jmx`) |

Scripts live under **`load-test/`** at project root (e.g. `load-test/gatling`, `load-test/k6`). Add a **README** in `load-test/` describing how to run each script and target metrics.

## Scenario description

1. **Ramp-up:** Start with a small number of virtual users (e.g. 5), ramp to target (e.g. 50–100) over 30–60 seconds.
2. **Seat map:** Each user repeatedly calls **GET /flights/{flightId}/seats** for a set of flight IDs (e.g. 1–10). Measure latency (p50, p95, p99) and error rate. **Target: P95 &lt; 1s.**
3. **Mix (optional):** Add a percentage of users performing **hold → confirm** (POST hold, then POST confirm with reservationId). Use distinct passengerIds and a pool of seats so some contention occurs.
4. **Concurrency assertion:** For the same seat, run many concurrent hold requests (same flightId, same seatId, different passengerIds). After the run, assert **at most one CONFIRMED or HELD reservation per seat** (query DB or GET seat map). Use **PostgreSQL** for this test (ARCHITECTURE_DESIGN.md §8).

## Where to add scripts

- Create **`load-test/`** in the project root.
- Add **`load-test/README.md`** with: how to run each script, required env (base URL, flight IDs), and target metrics (P95 &lt; 1s, 0% duplicate assignments).
- Do not commit large result artifacts; add `load-test/**/results/` to `.gitignore` and document how to export reports (e.g. Gatling HTML report path).

## Example commands (conceptual)

```bash
# Gatling (if scripts are in load-test/gatling)
cd load-test/gatling && mvn gatling:test

# k6
k6 run load-test/k6/seat-map.js
k6 run --vus 50 --duration 60s load-test/k6/seat-map.js
```

## Example script outline (seat map, k6-style)

- Set base URL (e.g. env or variable).
- Loop: pick a flightId from a list (e.g. 1–10), GET `/flights/${flightId}/seats`, check status 200, record response time.
- Optionally: threshold p95 &lt; 1000 ms.

## Environment

- Run against a local or staging instance: `mvn spring-boot:run` with dev profile, or **Docker Compose**.
- For **concurrency and single-winner** validation, use **PostgreSQL** (Option 2 or 3 in README); H2 does not provide the same locking guarantees.

## Note

Full load-test implementation is not in scope for the initial deliverable; this document describes the approach, scenario, and where to add scripts. Implement when validating NFRs (P95 &lt; 1s) or before production rollout.
