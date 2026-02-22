# Architecture

Modular monolith: Spring Boot 3, Java 17, PostgreSQL. Layers: Interface, Application, Domain, Infrastructure. Pessimistic locking for seats. Cache for seat map. Scheduled job for hold expiry.
