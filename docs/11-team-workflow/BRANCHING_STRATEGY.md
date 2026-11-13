# Branching strategy

**Purpose/scope:** lightweight pull-request collaboration. `main` remains stable; work on feature branches and merge via reviewed PR. Current Phase 4 work branch: `feature/phase-4-distributed-state-cart` (based on `main` at `aa9945c`); do not push or merge without authorization.

Names: `feature/<feature-name>`, `fix/<bug-name>`, `docs/<documentation-name>`, `infra/<infrastructure-name>`, `test/<testing-name>`. Keep branches narrow and up to date using non-destructive merge/rebase agreed by contributors. Do not force-push shared branches or commit credentials. Conventional commit examples: `feat(api): add health endpoint`, `docs(architecture): define network boundaries`, `test(web): cover unavailable API state`. Merge only after CI/required review and docs update. No repository branch-protection setting is changed by this project work.

Phase 4 ownership remains collaborative: Shiva coordinates architecture/session security/CI/integration review; Harivikash owns backend session/cart persistence/API/concurrency; Kari owns customer cart UX/frontend tests/docs support. Coordinate migration and endpoint contracts before concurrent changes, and do not overwrite another teammate's work.
