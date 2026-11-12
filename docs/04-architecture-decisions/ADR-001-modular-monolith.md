# ADR-001: Begin with a modular monolith

- **Status:** Accepted for initial scope
- **Date:** 2026-11-12
- **Context/problem:** small team and bounded academic application need delivery speed and transaction integrity; premature distributed services add deployment, network, observability and consistency failure modes.
- **Options:** modular monolith; microservices; serverless-first functions.
- **Decision:** one Spring Boot application organized by domain boundaries; allow independently deployed background workers only when asynchronous scaling/retry/ownership justifies it.
- **Justification/advantages:** one transactional boundary for initial order/inventory, lower operational burden, easier testing and refactoring; supports horizontal replicas.
- **Trade-offs/consequences:** teams must enforce module boundaries; shared runtime can cause noisy-neighbor scaling and coordinated releases; worker contracts require eventual consistency/idempotency.
- **Reconsider when:** measured independent scaling, release, fault-isolation or ownership constraints outweigh distributed complexity.
- **Validation/dependencies:** architecture tests and integration coverage; related FR-005, ADR-003/006. No service extraction is implemented.
