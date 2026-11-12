# ADR-012: Flyway owns schema evolution

- **Status:** Accepted for Phase 2
- **Date:** 2026-11-12
- **Context:** schema changes need reviewable, repeatable history across local development and CI.
- **Options:** Hibernate schema creation/update; manual SQL; versioned Flyway SQL migrations.
- **Decision:** use versioned Flyway SQL migrations. Hibernate is configured with `ddl-auto=validate`; application startup never mutates a production schema. Seed only the fixed CUSTOMER and ADMIN role records in the first migration.
- **Safety:** do not edit an applied migration; add a later forward migration. No destructive DDL, demo user or administrator password is introduced.
- **Trade-offs:** migrations add a required release/startup dependency and need explicit rollback planning; do not rely on automatic rollback.
- **Validation:** Testcontainers PostgreSQL test executes Flyway and checks application startup/schema/repository operations. This is Docker-dependent.
