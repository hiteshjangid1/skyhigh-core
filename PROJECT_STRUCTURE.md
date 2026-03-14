# Project Structure

```
skyhigh-core/
├── src/main/java/com/skyhigh/
│   ├── SkyhighCoreApplication.java     # Entry point; @SpringBootApplication
│   ├── domain/                         # Domain entities and enums
│   │   ├── flight/entity/              # Flight
│   │   ├── seat/entity/                # Seat, SeatReservation; SeatState enum
│   │   ├── checkin/entity/             # CheckIn; CheckInStatus enum
│   │   ├── baggage/entity/             # BaggageRecord
│   │   ├── waitlist/entity/            # WaitlistEntry; status PENDING/ASSIGNED
│   │   └── abuse/entity/               # AccessLog, AbuseEvent
│   ├── application/                    # Use cases / application services
│   │   ├── seat/                       # SeatService (hold, confirm, cancel, releaseExpiredHolds)
│   │   ├── checkin/                    # CheckInService (start, addBaggage, completePayment, getCheckIn)
│   │   ├── waitlist/                   # WaitlistService (join, getStatus, onSeatReleased); WaitlistNotificationSender
│   │   └── abuse/                      # AbuseDetectionService (recordAccess, checkAbuse)
│   ├── infrastructure/                 # Persistence and external integrations
│   │   ├── persistence/                # JPA repositories (Flight, Seat, SeatReservation, CheckIn, WaitlistEntry, AbuseEvent, etc.)
│   │   ├── external/                   # WeightServiceClient, PaymentServiceClient (interfaces + stubs)
│   │   └── config/                     # WebConfig, AsyncConfig, etc.
│   └── interfaces/                     # REST API and cross-cutting
│       ├── rest/controller/            # SeatController, CheckInController, WaitlistController, FlightController, AdminAbuseController
│       ├── rest/dto/                   # Request/response DTOs (HoldRequest, ConfirmRequest, ErrorResponse, etc.)
│       ├── rest/exception/             # GlobalExceptionHandler; custom exceptions (HoldExpiredException, AbuseDetectedException)
│       ├── rest/filter/                 # AbuseDetectionFilter (@Order(1), runs before controllers)
│       └── rest/interceptor/            # (optional) logging or correlation ID
├── src/main/resources/
│   ├── application.yml                 # Base config
│   ├── application-dev.yml             # H2, no Redis/RabbitMQ
│   ├── application-docker.yml          # PostgreSQL, optional Redis
│   ├── application-test.yml            # Test profile
│   └── db/migration/                   # Flyway: V1__..., V2__..., etc.
└── src/test/java/com/skyhigh/
    ├── application/                    # SeatServiceTest, CheckInServiceTest, WaitlistServiceTest, AbuseDetectionServiceTest
    └── interfaces/rest/                # SeatControllerIntegrationTest
```

## Key Modules and Classes

| Module | Purpose | Key classes / files |
|--------|---------|---------------------|
| **domain** | Entities and enums used by application and persistence | Flight, Seat, SeatReservation, SeatState; CheckIn, CheckInStatus; WaitlistEntry; AccessLog, AbuseEvent |
| **application** | Business logic and use cases | **SeatService**, **CheckInService**, **WaitlistService**, **AbuseDetectionService**; WaitlistNotificationSender (interface) |
| **infrastructure** | Persistence and external calls | *Repository interfaces/impls; WeightServiceClient, PaymentServiceClient; StubWeightServiceClient, StubPaymentServiceClient |
| **interfaces.rest** | HTTP API and error handling | **SeatController**, **CheckInController**, **WaitlistController**, **FlightController**, Admin controller for abuse; **GlobalExceptionHandler**; **AbuseDetectionFilter**; DTOs |
