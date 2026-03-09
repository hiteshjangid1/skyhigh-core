# Observability Strategy

## Logging

- **Levels:** Root INFO; `com.skyhigh` DEBUG in dev (configurable via `logging.level.com.skyhigh`).
- **What we log:** Application events (e.g. waitlist assignment, expired hold release failure), unhandled exceptions (ERROR in GlobalExceptionHandler). No PII in logs beyond passengerId where necessary; avoid logging full request bodies.
- **Structured logging:** Current format is standard Spring (pattern in application.yml if overridden). For production, consider JSON layout and correlation IDs (e.g. MDC) for request tracing.

## Metrics

- **Actuator:** Spring Boot Actuator is enabled; `/actuator/health` and `/actuator/metrics` are available. Default exposure can be limited in production (e.g. health only).
- **Custom metrics (Micrometer):**
  - **seat_holds_total** — Counter incremented in SeatService on each successful hold.
  - **abuse_events_total** — Counter incremented in AbuseDetectionService when an abuse event is saved (429).
  Both are registered via MeterRegistry and exposed under `/actuator/metrics` when the metrics endpoint is enabled.

## Alerting

- **Recommendations:** Alert on (1) high error rate (5xx), (2) abuse_events_total spike, (3) seat_holds_total drop if traffic is expected, (4) health check failures. Configure via your monitoring system (Prometheus, Datadog, etc.) using the Actuator metrics and health endpoints.
- **Runbook:** Document in operations: how to inspect abuse events (GET /admin/abuse/events), how to verify hold expiry job (logs), and how to clear cache if needed.
