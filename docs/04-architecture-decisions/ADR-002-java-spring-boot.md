# ADR-002: Java 25 and Spring Boot 4 for the API

- **Status:** Accepted; local build and test verified
- **Date:** 2026-11-12
- **Context/problem:** API needs mature HTTP, validation, operational endpoints and Java ecosystem; project baseline explicitly requests Java 25/Spring Boot 4.x.
- **Options:** Spring Boot 4.x; Spring Boot 3.x; Node/TypeScript API; other JVM frameworks.
- **Decision:** Java 25 and Spring Boot 4.1.2 with Maven, pinned parent and BOM. Official Spring project page reported 4.1.2 on selection date.
- **Justification/advantages:** matches course baseline, Java 25 LTS, established validation/Actuator/testing ecosystem.
- **Trade-offs/consequences:** new major line may bring migration/API incompatibility; ecosystem plugins and container images must be verified. Java/Maven prerequisites must be explicit.
- **Reconsider when:** compatibility/security support changes or build ecosystem does not support Java 25/Boot 4.
- **Validation/dependencies:** Java 25 + Maven 3.9.11 `verify` passed with 2 tests and no failures; Mockito emits a JDK agent deprecation warning. Related toolchain doc.
