# Security Considerations

## Current Security Model

- **Authentication:** Not implemented. All API endpoints are **unauthenticated**. This is acceptable for local development and evaluation only.
- **Authorization:** Not implemented. Any client can call any endpoint (e.g. confirm or cancel on behalf of any passengerId). Production would require scoping (e.g. cancel only own reservation).
- **Production recommendation:** Use authentication (e.g. JWT or API key) and enforce authorization so that:
  - Confirm and cancel are allowed only for the passenger who owns the reservation.
  - Admin/audit endpoints (e.g. GET /admin/abuse/events) are restricted to operators (e.g. API key or role).

## Input Validation

- **Request bodies:** Validated with Bean Validation (`@NotNull`, `@Size`, etc.) on DTOs. Invalid payloads return 400 Bad Request with a message listing validation errors.
- **Path/query parameters:** Validated where applicable; invalid IDs typically result in 404 or 400.

## SQL Injection

- **Mitigation:** All database access is through Spring Data JPA / Hibernate with parameterized queries. No raw SQL concatenation of user input. Flyway migrations are applied from project resources, not user input.

## PII and Sensitive Data

- **passengerId** is stored and logged. Treat it as PII in production; ensure access logs and audit endpoints are restricted and compliant with data retention policies.
- **No passwords or payment credentials** are stored by this service; payment is delegated to an external Payment service (stub in dev).

## Transport and Deployment

- **HTTPS:** Use TLS in production. This application does not enforce HTTPS; it should be deployed behind a reverse proxy or load balancer that terminates TLS.
- **Secrets:** Database and external service credentials should be supplied via environment variables or a secret manager, not committed to the repository. Default values in `application.yml` are for local use only.

## Abuse Detection and Rate Limiting

- Abuse detection applies to **GET /flights/{id}/seats** (seat map). When a source (e.g. IP) accesses too many distinct flight seat maps in a short window (default 50 in 2 seconds), the server returns **429 Too Many Requests** with a **Retry-After** header. See README and `docs/ABUSE_AND_RATE_LIMITING.md` for details.
- This is application-level protection; consider additional rate limiting at the gateway in production.
