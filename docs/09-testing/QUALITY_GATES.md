# Quality gates

**Purpose/scope:** minimum merge evidence; enforce in pull requests. No gate implies cloud deployment or production readiness.

| Area | Gate |
|---|---|
| Web | npm lockfile reproducible install, ESLint, strict TypeScript, Vitest, Vite production build |
| API | Java 25, Maven verify, unit/integration tests; no skipped test silently counted |
| Local stack | Compose config valid; PostgreSQL/Redis/API/web health, Redis-private networking, readiness, API replica/restart and Redis outage where daemon available |
| Security | secret scan; dependency review/scanning; container and Terraform scans when files/images exist; triage findings |
| Docs | internal links, Mermaid review, requirement IDs/ADR completeness, honest status and evidence |
| Workflow | minimal `GITHUB_TOKEN`, actions pinned by immutable SHA when available, no cloud credentials |

Block merge on build/test failure, exposed credential, unreviewed critical finding, missing requirement/security impact documentation, or unsafe infrastructure diff. Record accepted warnings and owner/expiry. Validate gates by inspecting CI run, not badge alone.

Phase 4 integration tests require PostgreSQL and Redis Testcontainers. A Maven run without a working Docker engine must report those suites as blocked/skipped; a green non-container Maven build is not proof of schema migration, distributed-session, Redis-outage, or cart-concurrency behavior.
