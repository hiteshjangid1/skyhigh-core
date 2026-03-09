# SkyHigh Core – Feedback Analysis and Comprehensive Task List

This document analyzes the evaluation feedback in depth and provides a prioritized, actionable task list. **No code changes are made here; this is analysis and planning only.**

---

## Part 1: In-Depth Analysis of Feedback

### 1. AI Collaboration

| Feedback Point | Analysis | Current State | Gap |
|----------------|----------|---------------|-----|
| **Minimal architectural dialogue or trade-off exploration using AI** | The reviewer expects evidence of back-and-forth with the AI on architecture (e.g., "What if we used optimistic locking?", "How do we handle multi-node hold expiry?"). | CHAT_HISTORY shows: one-shot context setting, one request for "design architecture + pros/cons + tools + tasks," then "implement Phase 0–10." No documented exploration of alternatives (e.g., optimistic vs pessimistic, Redis TTL vs job, multi-instance locking). | No artifact showing *exploration* of options; only final decisions. |
| **Collaboration largely command-driven** | Commands like "Start implementation," "Fix 500 error," "Add Postman," "Ensure 80% coverage" drive the work. | Most interactions are task execution: implement phases, fix bugs, add tests, fix JSON. | Lack of prompts that ask for reasoning, trade-off comparison, or "what are the risks of X?" |
| **Limited deep reasoning about concurrency or system design challenges** | Expectation: explicit reasoning about race conditions, distributed scenarios, hold expiry boundaries. | Concurrency is mentioned in PRD and design (pessimistic locking, single DB). No documented discussion of: H2 vs PostgreSQL locking, multi-instance deployment, or edge cases (e.g., confirm at exact expiry time). | No written reasoning or decision log for concurrency and distributed behavior. |

**Root cause:** The collaboration pattern was "define once, then execute." There was no deliberate phase of "explore alternatives with AI and document reasoning."

---

### 2. Advanced Architecture Gaps

| Feedback Point | Analysis | Current State | Gap |
|----------------|----------|---------------|-----|
| **No explicit rate limiting strategy exposed via API responses for bot detection** | Clients and operators need to know *why* they got 429 and *when* they can retry. | `AbuseDetectionFilter` returns 429 with a JSON body; it does **not** set standard headers such as `Retry-After` or `X-RateLimit-*`. API-SPECIFICATION.yml does **not** document 429 for GET seat map or any rate-limit semantics. | (1) No rate-limit/retry headers in 429 responses. (2) No API contract for 429 or rate-limiting behavior. |
| **Event-driven architecture for waitlist assignment notifications not fully developed or externally documented** | Expectation: event-driven waitlist is a first-class design with clear docs and possibly external notification. | Internally: `SeatReleasedEvent` → `WaitlistService.onSeatReleased` (@Async). No RabbitMQ publish, no notification stub (email/push), no `WaitlistAssignedEvent` documented or exposed. WORKFLOW_DESIGN.md and README do not describe the event flow or how passengers are "notified." | (1) Notifications are in-memory only; no external notification path. (2) No documentation of event flow, sequence, or failure handling. |
| **Abuse detection exists internally but lacks API-level transparency (e.g., audit retrieval endpoints)** | Operators need to inspect abuse events (who, when, how many). | Abuse events are persisted in `abuse_events` and logged; there is **no** REST endpoint to list or retrieve abuse events or access logs. API-SPECIFICATION.yml does not mention abuse or audit. | No GET (or similar) endpoint for abuse/audit data; no API contract for observability of abuse. |

---

### 3. Implementation

| Feedback Point | Analysis | Current State | Gap |
|----------------|----------|---------------|-----|
| **API specification lacks clearly documented error handling for seat conflicts and hold expiry (e.g., consistent 409/410 semantics)** | API contract should explicitly define when 409 (Conflict) and 410 (Gone) are returned and the response body shape. | GlobalExceptionHandler maps: SeatUnavailableException → 409, HoldExpiredException → 410. API-SPECIFICATION.yml documents 409 for hold and 410 for confirm, but: (1) No **schema** for error response body. (2) Confirm does not document 400/404 for wrong passenger or not found. (3) Hold does not document 400 for invalid payload. (4) Other endpoints (check-in, cancel, waitlist) lack documented error responses. | Inconsistent and incomplete error documentation; no shared ErrorResponse schema; not all endpoints document 4xx/5xx. |
| **No authentication or authorization implemented** | Production APIs typically require auth and scope (e.g., passenger can only cancel own reservation). | No Spring Security, no JWT/OAuth, no API key. All endpoints are unauthenticated. | No auth/authz; no doc on "future auth" or security model. |
| **No explicit validation documentation for request objects or concurrency edge cases** | Contract should state required fields, formats, and behavior under concurrent requests. | Bean Validation exists on DTOs but API-SPECIFICATION.yml does not list required fields, max lengths, or validation rules. No description of concurrent behavior (e.g., "if two holds for same seat, one receives 409"). | OpenAPI lacks validation details and concurrency semantics. |

---

### 4. Documentation & Quality

| Feedback Point | Analysis | Current State | Gap |
|----------------|----------|---------------|-----|
| **Missing detailed documentation for waitlist event notifications and abuse detection operations** | Dedicated docs for these flows. | WORKFLOW_DESIGN.md has one line on waitlist; no sequence diagram, no event list, no abuse flow. README mentions abuse config but not behavior or API. | No ADR or dedicated section for waitlist events and abuse (flow, thresholds, response). |
| **README does not comprehensively describe advanced flows or rate-limiting behavior** | README as single entry point for behavior. | README covers run, config, tests. It does not describe: seat lifecycle, hold expiry, waitlist assignment, rate-limiting/429 behavior, or error codes. | README lacks "Behavior" and "API behavior" sections. |
| **No documented security considerations** | Even without auth, security should be discussed. | No SECURITY.md or README section on: input validation, injection, PII, HTTPS, or threat model. | No security section. |
| **No explicit concurrency or hold-expiry edge-case test evidence** | Tests that demonstrate correct behavior under concurrency and expiry. | SeatServiceTest has `confirmSeat_whenExpired_throws` and `releaseExpiredHolds_releasesAndPublishesEvent`. No test that simulates "confirm at T+119s vs T+121s," or concurrent hold attempts (integration test disabled). No documented test matrix for edge cases. | Missing: concurrent hold test (enabled or alternative), hold-expiry boundary tests, and a doc listing edge-case tests. |
| **No load testing instructions for seat map performance** | NFR was P95 < 1s; reviewers need to verify. | DEVELOPMENT_TASKS mentions load test; README and project have no Gatling/JMeter/k6 script or instructions. | No load-test guide or artifact. |
| **Architecture documentation does not fully align with distributed concurrency design** | Doc should reflect reality: single-node vs multi-node, H2 vs PostgreSQL locking. | ARCHITECTURE_DESIGN describes pessimistic locking; implementation uses `findById` in dev (H2). No section on "Deployment: single instance vs multiple," or "Locking behavior per environment." | Architecture doc does not describe dev vs prod locking or multi-instance implications. |
| **No enterprise observability/monitoring (metrics, logging strategy, alerting) documented or implemented** | Production readiness. | Actuator is present; no custom metrics (e.g., hold count, abuse count), no structured logging strategy, no alerting or runbook. | No observability doc; no metrics/logging/alerting design. |

---

### 5. Data Integrity & Schema Design

| Feedback Point | Analysis | Current State | Gap |
|----------------|----------|---------------|-----|
| **No detailed database schema documentation for seat state tracking and hold management** | Schema doc for tables, columns, constraints, and state semantics. | WORKFLOW_DESIGN only lists table names. Migrations (V1–V5) define tables and indexes but there is no standalone schema document describing: state columns, allowed values, hold_until semantics, or relationships. | No SCHEMA.md or equivalent with ER and state lifecycle. |
| **No explicit discussion of data integrity enforcement at the database level** | DB-level constraints (CHECK, FK, unique) that enforce invariants. | V1 has FK and unique (flight_id, seat_number). There are **no** CHECK constraints on `state` (e.g., seats.state IN ('AVAILABLE','HELD','CONFIRMED')) or on reservation state/held_until. Integrity is enforced only in application code. | No doc on DB-level integrity; no CHECK constraints for state. |

---

## Part 2: Comprehensive Task List

Tasks are grouped by theme, with priority (P1 = high, P2 = medium, P3 = lower), dependencies, and acceptance criteria. **Coding is not started; this is the plan.**

---

### Theme A: AI Collaboration and Design Reasoning

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| A1 | **Create an Architecture Decision Record (ADR) or design log** that documents 3–5 key decisions (e.g., pessimistic vs optimistic locking, scheduled job vs Redis TTL, modular monolith vs microservices) with *alternatives considered*, *rationale*, and *consequences*. Use this as the basis for future AI dialogue. | P1 | None | One ADR document (or section in ARCHITECTURE_DESIGN) with at least 3 decisions and alternatives. |
| A2 | **Enrich CHAT_HISTORY.md** with a short "Design dialogue" section: add 2–3 example prompts that could have been used to explore trade-offs (e.g., "Compare optimistic vs pessimistic locking for seat hold; list pros/cons and when each fails") and summarize how such dialogue would change the written design. | P2 | A1 | CHAT_HISTORY contains an explicit "Design exploration" subsection with example prompts and intended outcomes. |
| A3 | **Document concurrency and distributed design** in ARCHITECTURE_DESIGN: single-instance vs multi-instance behavior, H2 vs PostgreSQL locking, and edge cases (e.g., confirm at expiry boundary, concurrent hold). Align doc with actual implementation (findById in dev vs findByIdForUpdate in prod if reintroduced). | P1 | None | Architecture doc has a "Concurrency and deployment" section that matches implementation and states limitations. |

---

### Theme B: Rate Limiting and Abuse Detection API Transparency

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| B1 | **Define and implement rate-limit semantics in API responses** when abuse is detected (429): add `Retry-After` header (e.g., seconds until window reset) and optionally `X-RateLimit-*` headers. Document behavior in API-SPECIFICATION.yml. | P1 | None | 429 responses include Retry-After; OpenAPI documents 429 for GET seat map and describes headers. |
| B2 | **Add audit/abuse retrieval endpoints** (e.g., GET /admin/abuse/events or /audit/abuse with optional filters by source, time range). Protect by role or API key if auth is added later; until then, document as "admin" and that auth is TBD. Document in API-SPECIFICATION.yml. | P1 | None | At least one read endpoint for abuse events; API spec updated; README or OPERATIONS.md mentions audit. |
| B3 | **Document abuse detection in README and a dedicated doc**: threshold (50 flights in 2s), which endpoints are protected, response (429 + body), and how to interpret Retry-After. | P2 | B1 | README has "Abuse detection" subsection; optional ABUSE_AND_RATE_LIMITING.md or equivalent. |

---

### Theme C: Event-Driven Waitlist and Notifications

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| C1 | **Document event-driven waitlist flow** in WORKFLOW_DESIGN or a new WAITLIST_DESIGN.md: sequence from seat release → SeatReleasedEvent → WaitlistService → hold for next passenger; document @Async and transaction boundaries; document failure behavior (hold fails → seat stays available). | P1 | None | Document contains sequence and failure semantics. |
| C2 | **Add optional notification hook** (e.g., interface `WaitlistNotificationSender` with stub implementation) when a waitlist entry is assigned, and call it from WaitlistService. Document in architecture/design as "notification extension point." | P2 | C1 | Interface exists; stub implementation; design doc updated. |
| C3 | **Document in API or README** that waitlist assignment is asynchronous and that clients should poll GET waitlist status to see ASSIGNED state; no synchronous "notify me" API required for MVP. | P2 | C1 | README or API spec states polling and async assignment. |

---

### Theme D: API Contract and Error Handling

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| D1 | **Introduce a reusable error response schema** in API-SPECIFICATION.yml (e.g., `ErrorResponse`: timestamp, status, error, message, path, optional code). Reference it in all documented error responses. | P1 | None | OpenAPI has components.schemas.ErrorResponse; all 4xx/5xx reference it. |
| D2 | **Document all error responses per endpoint**: 400, 404, 409, 410, 429, 500 where applicable. Explicitly document 409 for seat conflict (hold/confirm) and 410 for hold expired (confirm). Add short descriptions (e.g., "Seat unavailable or already held"). | P1 | D1 | Every documented endpoint lists relevant error codes with descriptions. |
| D3 | **Document validation rules** in OpenAPI: required fields, string length limits, format (e.g., passengerId), and example invalid payloads where useful. | P2 | None | Request body schemas include required array and property constraints. |
| D4 | **Document concurrency semantics** in API spec (e.g., for hold: "If two requests target the same AVAILABLE seat, one succeeds with 200 and one fails with 409"). | P2 | None | Hold and confirm endpoints have a "Concurrency" or "Conflict" note in description. |

---

### Theme E: Authentication and Authorization

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| E1 | **Document the current security model** in README and optionally SECURITY.md: no auth today, APIs are unauthenticated, and that production would require auth (e.g., JWT or API key). List which operations would be scoped (e.g., cancel only own reservation). | P1 | None | README has "Security" section; SECURITY.md optional. |
| E2 | **Implement optional authentication** (e.g., Spring Security with API key or JWT in a dedicated profile so dev can stay unauth). Document in API-SPECIFICATION (securitySchemes, which endpoints require auth). | P2 | E1 | Auth can be enabled via profile; OpenAPI documents security. |
| E3 | **Implement authorization rules** (e.g., confirm/cancel only for the passenger who holds/owns the reservation). | P2 | E2 | Service or controller enforces ownership where applicable. |

---

### Theme F: Documentation and README

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| F1 | **Expand README** with "Behavior" or "Features": seat lifecycle (AVAILABLE→HELD→CONFIRMED), hold expiry (2 min, job every 15s), check-in flow, waitlist assignment, abuse/rate limiting (429), and link to API spec and architecture. | P1 | None | README has a clear behavior section covering all major flows. |
| F2 | **Add "Security considerations"** to README or SECURITY.md: input validation, SQL injection mitigation (JPA), PII handling (passengerId), HTTPS recommendation, no auth in dev. | P1 | None | Security section exists and is accurate. |
| F3 | **Document load testing approach**: tool (e.g., Gatling, k6, or JMeter), how to run, target (e.g., P95 < 1s for GET seat map), and where to add scripts (e.g., /load-test). No obligation to implement full suite yet. | P2 | None | README or LOAD_TESTING.md describes approach and commands. |

---

### Theme G: Testing and Evidence

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| G1 | **Add hold-expiry edge-case tests** (e.g., confirm when held_until is in the past; confirm when held_until is exactly now; releaseExpiredHolds only releases past holds). Document in test class or TESTING.md. | P1 | None | At least 2 tests for expiry boundaries; doc or comments reference them. |
| G2 | **Re-enable or replace concurrent hold integration test**: either enable with Testcontainers/PostgreSQL, or add a documented "concurrency test" that runs with real DB and asserts single winner. Document why it was disabled and how to run it. | P1 | None | One test proves single-winner behavior or doc explains how to run it (e.g., Docker profile). |
| G3 | **Create TESTING.md** (or section in README): list unit tests by area, integration tests, coverage targets, and **concurrency and hold-expiry edge-case tests** with file/location. | P2 | G1, G2 | TESTING.md exists and lists edge-case and concurrency tests. |

---

### Theme H: Observability and Operations

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| H1 | **Document observability strategy**: logging (what is logged, level), metrics (what to expose, e.g., hold count, abuse count, seat map latency), and alerting (what to alert on). No full implementation required initially. | P2 | None | OBSERVABILITY.md or README section describes strategy. |
| H2 | **Add a few custom metrics** (e.g., Micrometer counters for seat_holds_total, abuse_events_total) and expose via Actuator. Document in observability doc. | P3 | H1 | At least 2 custom metrics; doc updated. |

---

### Theme I: Data Integrity and Schema Documentation

| ID | Task | Priority | Dependencies | Acceptance Criteria |
|----|------|----------|--------------|---------------------|
| I1 | **Create SCHEMA.md** (or extend WORKFLOW_DESIGN): list all tables, columns, FKs, unique constraints, and indexes. For seats and seat_reservations, document state values, held_until semantics, and how they relate to the seat lifecycle. | P1 | None | Single document describes schema and state lifecycle in DB. |
| I2 | **Evaluate and document DB-level integrity**: whether to add CHECK constraints for state columns (e.g., seats.state IN ('AVAILABLE','HELD','CONFIRMED')). If not added, document that integrity is enforced in application and why. | P2 | I1 | Architecture or SCHEMA doc states approach and rationale. |
| I3 | **Optionally add CHECK constraints** in a new Flyway migration for seat and reservation states. | P3 | I2 | Migration adds CHECKs; no regression. |

---

## Part 3: Suggested Implementation Order

1. **Quick wins (docs only, no code):** A1, A3, D1, D2, D4, E1, F1, F2, I1.  
2. **API and behavior (code + doc):** B1, B2, B3, C1, C3, D3.  
3. **Tests and evidence:** G1, G2, G3.  
4. **Design and events:** A2, C2.  
5. **Auth (optional):** E2, E3.  
6. **Observability and schema hardening:** H1, H2, I2, I3, F3.

---

## Part 4: Mapping Back to Feedback

| Feedback Category | Task IDs |
|-------------------|----------|
| AI collaboration | A1, A2, A3 |
| Rate limiting / abuse API | B1, B2, B3 |
| Event-driven waitlist / notifications | C1, C2, C3 |
| API error handling & validation | D1, D2, D3, D4 |
| Auth/authz | E1, E2, E3 |
| Documentation & README | F1, F2, F3 |
| Concurrency / hold-expiry tests | G1, G2, G3 |
| Observability | H1, H2 |
| Data integrity & schema | I1, I2, I3 |

---

**Next step:** Choose a theme (e.g., A or D) and execute tasks in order; then iterate. No coding has been done in this document; it is analysis and a task list only.
