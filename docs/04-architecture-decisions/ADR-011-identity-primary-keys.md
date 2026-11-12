# ADR-011: UUID primary keys for identity

- **Status:** Accepted for Phase 2
- **Date:** 2026-11-12
- **Context:** user and role identifiers cross application/API boundaries and may later be created independently across environments.
- **Options:** PostgreSQL identity integers; application-generated UUIDv4; time-ordered UUIDv7.
- **Decision:** use application-generated UUIDv4 primary keys for users and UUID primary keys for roles. Do not expose database sequence position as identity. Email is normalized using Java `Locale.ROOT` and protected by a PostgreSQL unique index.
- **Trade-offs:** random UUIDs use wider indexes and have less locality than sequential or time-ordered identifiers. UUIDv7 is deferred until supported by the pinned Java/JPA stack and justified by measured write behavior.
- **Validation:** Flyway migration and repository integration tests against PostgreSQL; migration is immutable once applied.
