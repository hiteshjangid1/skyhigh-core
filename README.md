# SkyHigh Core – Digital Check-In System

Backend service for SkyHigh Airlines self-check-in, handling seat management, baggage validation, waitlist, and abuse detection.

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for full stack)

## Quick Start (Local Development)

### Option 1: Standalone with H2 (no Docker)

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Windows PowerShell:** Use quotes so `-D` is passed correctly to Maven:
```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

**Or use the batch script (Windows):** `run-dev.bat`

Uses in-memory H2 database. Redis and RabbitMQ are disabled.

### Option 2: With Docker (PostgreSQL, Redis, RabbitMQ)

```bash
docker compose up -d postgres redis rabbitmq
mvn spring-boot:run "-Dspring-boot.run.profiles=default"
```

**Windows PowerShell:**
```powershell
docker compose up -d postgres redis rabbitmq
mvn spring-boot:run "-Dspring-boot.run.profiles=default"
```

### Option 3: Full Docker Compose

```bash
docker compose up --build
```

Starts PostgreSQL, Redis, RabbitMQ, and the application. API available at http://localhost:8080.

## Database Setup

### PostgreSQL (Docker)

```bash
docker compose up -d postgres
```

Flyway runs automatically on startup and applies migrations from `src/main/resources/db/migration/`.

### H2 (Development)

No setup required. Flyway creates schema in-memory.

## Running the Application

```bash
mvn spring-boot:run
```

Default profile: `dev` (H2, no Redis/RabbitMQ). Override: `mvn spring-boot:run "-Dspring-boot.run.profiles=docker"` when using Docker infra.

## Background Workers

The hold-expiry job runs every 15 seconds (`@Scheduled`) to release seats held longer than 2 minutes. No separate worker process is needed.

## API Documentation

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/v3/api-docs

## Tests

```bash
mvn test
```

Unit tests run with H2. The integration test `SeatControllerIntegrationTest` is disabled (requires PostgreSQL for pessimistic locking). Coverage report: `target/jacoco-report/index.html`

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| skyhigh.seat.hold-duration-seconds | 120 | Seat hold validity |
| skyhigh.baggage.max-weight-kg | 25 | Max baggage weight |
| skyhigh.abuse.threshold-count | 50 | Abuse: distinct flights in window |
| skyhigh.abuse.threshold-window-seconds | 2 | Abuse: time window |
