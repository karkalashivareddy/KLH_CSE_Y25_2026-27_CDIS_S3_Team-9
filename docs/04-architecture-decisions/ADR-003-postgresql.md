# ADR-003: PostgreSQL as transactional system of record

- **Status:** Accepted for target
- **Date:** 2026-11-12
- **Context/problem:** orders, inventory and identity require relational constraints, transactions and queryability.
- **Options:** PostgreSQL; document database; distributed SQL; multiple databases.
- **Decision:** PostgreSQL for core transactional data; Redis only for disposable cache/rate-limit-like data, not authoritative orders.
- **Justification/advantages:** relational integrity and mature transaction/backup ecosystem; compatible managed offerings across target clouds.
- **Trade-offs/consequences:** scaling writes and failover require operational design; managed versions/extensions differ; local compose does not imply production-ready HA.
- **Reconsider when:** workload or access patterns demonstrate materially better fit elsewhere.
- **Validation/dependencies:** Phase 2 adds PostgreSQL wiring, Flyway identity schema and Testcontainers integration coverage. Container execution still requires a Docker-capable test host; production restore/concurrency validation remains future work.
