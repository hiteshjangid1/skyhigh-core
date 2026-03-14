# Security Considerations

This document describes the **current security model**, **input validation**, **mitigations** (SQL injection, PII, transport), and a **threat model** with identified threats and mitigations. It is the single place in the repository for security considerations and threat modeling.

---

## Current Security Model

- **Authentication:** Not implemented. All API endpoints are **unauthenticated**. This is acceptable for local development and evaluation only.
- **Authorization:** Not implemented. Any client can call any endpoint (e.g. confirm or cancel on behalf of any passengerId). Production would require scoping (e.g. cancel only own reservation).
- **Production recommendation:** Use authentication (e.g. JWT or API key) and enforce authorization so that:
  - Confirm and cancel are allowed only for the passenger who owns the reservation.
  - Admin/audit endpoints (e.g. GET /admin/abuse/events) are restricted to operators (e.g. API key or role).

## Input Validation

- **Request bodies:** Validated with Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, etc.) on DTOs. Invalid payloads return **400 Bad Request** with a message listing validation errors (e.g. "passengerId must not be blank").
- **DTOs and rules (examples):** Hold/confirm requests require non-null `passengerId` and (for hold) `flightId`, `seatId`. Check-in start requires `flightId`, `passengerId`, `seatId`. Waitlist join requires `passengerId`. Baggage requires `passengerId`, `baggageId`. Payment requires `passengerId`, `paymentRef`. Controllers use `@Valid` on request body; GlobalExceptionHandler maps `MethodArgumentNotValidException` to 400.
- **Path/query parameters:** Validated where applicable (e.g. numeric IDs). Invalid or missing IDs typically result in **404 Not Found** (resource not found) or **400** (e.g. invalid format).
- **Summary:** Validation is implemented (Bean Validation + 400) and documented in **API-SPECIFICATION.yml** (required, minLength, maxLength on request bodies). Authentication and authorization are documented above as not implemented, with production recommendations.

## SQL Injection

- **Mitigation:** All database access is through Spring Data JPA / Hibernate with parameterized queries. No raw SQL concatenation of user input. Flyway migrations are applied from project resources, not user input.

## PII and Sensitive Data

- **passengerId** is stored and logged. Treat it as PII in production; ensure access logs and audit endpoints are restricted and compliant with data retention policies.
- **No passwords or payment credentials** are stored by this service; payment is delegated to an external Payment service (stub in dev).

## Transport and Deployment

- **HTTPS:** Use TLS in production. This application does not enforce HTTPS; it should be deployed behind a reverse proxy or load balancer that terminates TLS.
- **Secrets:** Database and external service credentials should be supplied via environment variables or a secret manager, not committed to the repository. Default values in `application.yml` are for local use only.

## Abuse Detection and Rate Limiting

- Abuse detection applies to **GET /flights/{id}/seats** (seat map). When a source (e.g. IP) accesses too many distinct flight seat maps in a short window (default 50 in 2 seconds), the server returns **429 Too Many Requests** with a **Retry-After** header. See README "Rate-limiting strategy" and "Abuse detection and rate limiting (detailed)" for details.
- This is application-level protection; consider additional rate limiting at the gateway in production.

## Threat Model (Summary)

**Purpose:** The system is an unauthenticated API for dev/evaluation. The threat model below identifies main risks and how they are (or should be) addressed. For production, authentication, authorization, and operational hardening are required.

| Threat | Description / example | Mitigation |
|--------|----------------------|------------|
| **Unauthenticated access** | Any client can call any endpoint (e.g. confirm or cancel on behalf of another passengerId). Acceptable only for dev/evaluation. | Add authentication (JWT/API key) and authorization (e.g. cancel only own reservation) in production. |
| **Abuse / scraping** | Single IP can request many distinct flight seat maps to scrape availability. Example: 50+ flight IDs in 2 seconds. | Application-level abuse detection: 429 with Retry-After, persist abuse_events, GET /admin/abuse/events for audit. Consider gateway rate limiting in addition. |
| **Data exposure** | passengerId and reservation data appear in API responses and server logs. Audit endpoint exposes source and event data. | Restrict admin/audit endpoints; use HTTPS; apply PII retention and access control. |
| **Injection** | User input in request bodies (passengerId, baggageId, etc.) and path (flightId, seatId). Could be used in raw SQL if concatenated. | All DB access via JPA/Hibernate with parameterized queries; no string concatenation into SQL. Bean Validation on inputs. |
| **Denial of service** | High request volume can exhaust connections or CPU. | Rate limiting (abuse filter), connection pooling (HikariCP), and monitoring (OBSERVABILITY.md). |
