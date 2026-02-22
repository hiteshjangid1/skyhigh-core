# Project Structure

```
skyhigh-core/
├── src/main/java/com/skyhigh/
│   ├── SkyhighCoreApplication.java     # Entry point
│   ├── domain/                         # Domain entities
│   │   ├── flight/entity/
│   │   ├── seat/entity/
│   │   ├── checkin/entity/
│   │   ├── baggage/entity/
│   │   ├── waitlist/entity/
│   │   └── abuse/entity/
│   ├── application/                    # Use cases / services
│   │   ├── seat/
│   │   ├── checkin/
│   │   ├── waitlist/
│   │   └── abuse/
│   ├── infrastructure/                 # Persistence, external clients
│   │   ├── persistence/
│   │   ├── external/
│   │   └── config/
│   └── interfaces/                     # REST, filters, DTOs
│       ├── rest/controller/
│       ├── rest/dto/
│       ├── rest/exception/
│       ├── rest/filter/
│       └── rest/interceptor/
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-docker.yml
│   ├── application-test.yml
│   └── db/migration/                   # Flyway migrations
└── src/test/java/                      # Unit & integration tests
```

## Key Modules

| Module | Purpose |
|--------|---------|
| **domain** | Entities (Flight, Seat, CheckIn, etc.) and enums (SeatState, CheckInStatus) |
| **application** | Business logic: SeatService, CheckInService, WaitlistService, AbuseDetectionService |
| **infrastructure** | JPA repositories, Weight/Payment client stubs, WebConfig |
| **interfaces** | REST controllers, DTOs, GlobalExceptionHandler, AbuseDetectionFilter |
