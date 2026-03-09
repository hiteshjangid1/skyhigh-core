# Load Testing

## Goal

- **Seat map (GET /flights/{id}/seats):** P95 latency &lt; 1 second under load (NFR from PRD).
- **Concurrent seat holds:** Verify no duplicate seat assignments when many users hold/confirm concurrently.

## Approach

1. **Tool:** Use a load-testing tool such as **Gatling** (Scala DSL or Java), **k6** (JavaScript), or **JMeter**. Scripts can live in a `load-test/` directory at project root (e.g. `load-test/gatling`, `load-test/k6`).
2. **Scenario:** Ramp-up users hitting GET seat map for a fixed set of flight IDs; optionally mix in hold/confirm flows. Measure response times and error rate.
3. **Target:** P95 &lt; 1s for seat map; 0% duplicate assignments (assert one reservation per seat when running concurrent holds for the same seat).
4. **Environment:** Run against a local or staging instance (e.g. `mvn spring-boot:run` with dev profile, or Docker Compose). For concurrency guarantees use **PostgreSQL** (see ARCHITECTURE_DESIGN.md §8).

## Example (conceptual)

```bash
# Example: run Gatling (if scripts are in load-test/gatling)
cd load-test/gatling && mvn gatling:test

# Example: k6
k6 run load-test/k6/seat-map.js
```

## Where to add scripts

- Create `load-test/` in the project root.
- Add a README in `load-test/` describing how to run each script and the target metrics.
- Do not commit large result artifacts; document how to export reports (e.g. Gatling HTML report).

## Note

Full load-test implementation is not in scope for the initial deliverable; this document describes the approach and where to add scripts. Implement when validating NFRs (e.g. P95 &lt; 1s) or before production rollout.
