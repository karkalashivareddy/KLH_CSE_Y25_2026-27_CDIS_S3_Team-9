# Branching strategy

**Purpose/scope:** lightweight pull-request collaboration. `main` remains stable; work on feature branches and merge via reviewed PR. Current Phase 1 branch: `feature/phase-1-engineering-foundation`.

Names: `feature/<feature-name>`, `fix/<bug-name>`, `docs/<documentation-name>`, `infra/<infrastructure-name>`, `test/<testing-name>`. Keep branches narrow and up to date using non-destructive merge/rebase agreed by contributors. Do not force-push shared branches or commit credentials. Conventional commit examples: `feat(api): add health endpoint`, `docs(architecture): define network boundaries`, `test(web): cover unavailable API state`. Merge only after CI/required review and docs update. No repository branch-protection setting is changed by this project work.
