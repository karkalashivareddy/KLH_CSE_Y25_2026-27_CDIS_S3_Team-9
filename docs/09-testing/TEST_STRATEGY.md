# Test strategy

**Purpose/scope:** verification approach for implemented foundations and staged product growth. No test is counted as passing until run and recorded. Related: test plan, quality gates, requirement traceability.

1. **Unit:** pure domain rules/validation; no framework where not needed.
2. **Integration:** Spring context, persistence, queue contracts and component boundaries; use Testcontainers for real PostgreSQL/Redis where Docker is available.
3. **API/contract:** status/schema/auth/error semantics; provider ports tested with contract suites.
4. **Frontend:** Vitest/Testing Library for rendering, loading/error/accessibility and API states.
5. **E2E:** Playwright foundations for shopper/admin user journeys when those exist.
6. **Security:** negative authorization, malformed input, dependency/secrets/image/IaC scans.
7. **Performance:** repeatable workload, data volume, hardware, duration, p95 and error budget; do not infer from unit tests.
8. **Resilience:** dependency outage, retry/idempotency, backup restore, timed recovery, rollback and evidence.
9. **Infrastructure:** Compose validation/health; later Terraform fmt/validate/plan policy without provisioning.

Phase 1 scope: web unit test; API MockMvc tests; lint/typecheck/build/package; Compose config/service check if daemon works. Testcontainers integration is deferred until Maven and Docker daemon are available. Risks include environment-dependent Docker/network availability. Validation evidence belongs in completion report/CI.
