# Observability Strategy

## Logging

- **Levels:** Root **INFO**; `com.skyhigh` **DEBUG** in dev (set `logging.level.com.skyhigh=DEBUG` in application-dev.yml or override via env).
- **What is logged and when:**
  - **INFO:** Application startup, Flyway migrations.
  - **DEBUG:** Per-request or business events (e.g. hold requested, confirm succeeded) if enabled.
  - **WARN:** Recoverable business conditions (e.g. hold failed — seat unavailable).
  - **ERROR:** Unhandled exceptions in **GlobalExceptionHandler** (e.g. 500 responses); stack trace in dev, message only in production if configured.
  - **Waitlist:** WaitlistNotificationSender (e.g. StubWaitlistNotificationSender) may log when a passenger is assigned (INFO/DEBUG).
- **PII:** Avoid logging full request bodies. passengerId may appear in logs where necessary for debugging; in production consider redaction or minimal logging.
- **Structured logging:** Default format is Spring’s pattern (timestamp, level, logger, message). For production, use a JSON layout and **correlation IDs** (e.g. X-Request-ID in MDC) for request tracing.

## Metrics

- **Actuator:** Spring Boot Actuator is enabled. **`/actuator/health`** (liveness/readiness) and **`/actuator/metrics`** are available. Restrict exposure in production (e.g. health only for public; metrics behind internal scrape).
- **Custom metrics (Micrometer):**

| Metric name | Type | When incremented | How to query |
|-------------|------|-------------------|--------------|
| **seat_holds_total** | Counter | SeatService: each successful **holdSeat** (seat state → HELD). | GET /actuator/metrics/seat_holds_total or Prometheus scrape. |
| **abuse_events_total** | Counter | AbuseDetectionService: when an abuse event is **saved** (request exceeded threshold, 429 returned). | GET /actuator/metrics/abuse_events_total. |

- **Standard metrics:** JVM (memory, threads), HTTP (request count, latency) are exposed when Actuator metrics are enabled. Use them for error rate (e.g. `http.server.requests` with tag `status=500`).

## Alerting

- **What to alert on:** (1) **High 5xx rate** (e.g. > 1% of requests or > N in 5 min), (2) **abuse_events_total** spike (e.g. > 10 in 1 min), (3) **seat_holds_total** drop when traffic is expected (possible outage or upstream issue), (4) **Health check failures** (readiness/liveness down).
- **Suggested thresholds (tune per environment):** 5xx rate &lt; 1%; abuse_events_total &lt; 50/hour for normal operation; health down for &gt; 2 consecutive checks.
- **Runbook (abuse):** (1) Query **GET /admin/abuse/events?since=...&limit=500** to list recent events. (2) Identify source_id (IP). (3) If legitimate (e.g. aggregator), consider increasing threshold or whitelist; if malicious, block at firewall/WAF or keep 429.
- **Runbook (hold expiry):** (1) Check logs for "releaseExpiredHolds" or errors in SeatService. (2) Verify DB: `SELECT * FROM seat_reservations WHERE state = 'HELD' AND held_until < NOW();` should be empty after job run. (3) Ensure app is running and scheduled job is not disabled.

## Logging pipeline and alerting infrastructure

- **Logging pipeline:** Application logs (stdout/stderr or file) should be shipped to a central aggregation layer. Options: **Fluentd / Fluent Bit** → Elasticsearch (or OpenSearch), **Datadog Agent**, **Splunk**, or cloud-native (CloudWatch Logs, GCP Logging). Use **structured (JSON) logging** from the app so fields (level, logger, message, correlation ID) can be queried. Retention and access control should follow PII and compliance requirements.
- **Alerting infrastructure:** Metrics from Actuator (or Prometheus scrape) feed into an alerting system. Typical setup: **Prometheus** scrapes `/actuator/prometheus` (add `micrometer-registry-prometheus` for Prometheus endpoint) → **Alertmanager** evaluates rules (e.g. 5xx rate, abuse_events_total spike, health down) → notifications to **PagerDuty**, Slack, or email. Alternatively, **Datadog**, **New Relic**, or cloud monitoring (CloudWatch, GCP Monitoring) can scrape HTTP/metrics and define alerts. Document alert rules and runbooks (see Alerting and Runbook above) in your operations repo or OBSERVABILITY.md.

---

## Deployment and Operations

- **Audit API:** **GET /admin/abuse/events** (query params: sourceId, since, limit) returns recorded abuse events; documented in **API-SPECIFICATION.yml** and **README** (Rate-limiting strategy, Abuse detection). Use for inspecting abuse events (see Runbook (abuse) above).
- **Health:** Use **`/actuator/health`** in Docker healthcheck, Kubernetes liveness/readiness, or load balancer. Ensures app and dependencies (DB, optional Redis) are up.
- **Metrics:** Expose **`/actuator/metrics`** (or Prometheus scrape at `/actuator/prometheus` if dependency is added) so monitoring can collect seat_holds_total, abuse_events_total, and JVM/HTTP metrics. Configure alerting as above.
- **Logging:** In production use **structured (JSON) logging** and a log pipeline for search and alerting. **Correlation IDs** (X-Request-ID in MDC) help trace a request across logs and optional distributed tracing.
