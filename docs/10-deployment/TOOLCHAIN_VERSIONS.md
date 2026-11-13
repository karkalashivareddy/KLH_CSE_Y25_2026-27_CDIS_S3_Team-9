# Toolchain versions

**Purpose/scope:** pinned foundation versions for reproducible local builds; not a claim that every tool is installed. **Date checked:** 2026-11-12. **Assumptions:** official project pages and package registry metadata reflect current releases; refresh before upgrading.

| Component | Selected version | Rationale / evidence |
|---|---:|---|
| Java | 25 LTS | Supplied project baseline; local runtime verified as Java 25 (build 25+37). |
| Spring Boot | 4.1.2 | Official Spring Boot project page listed 4.1.2; Java 25 compatible baseline. Verify starter/dependency changes when compiling. |
| Maven | 3.9.11 | Pinned build image; local Maven 3.9.11 distribution in approved temp path used for Phase 4 checks. |
| Node.js | 24 LTS | Official Node releases page showed 24 in LTS; local v24.19.0. Vite guide requires >=20.19 or >=22.12. |
| npm | 11.x | Local 11.17.0; lockfile required for exact dependency tree. |
| React / React DOM | 19.3.1 | Registry version observed during setup; exact package pin. |
| React Router | 7.18.5 | Registry version observed; client-side routing foundation. |
| Vite | 8.3.8 | Registry version observed; official Vite guide consulted. |
| Vite React plugin | 6.3.0 | Exact package pin; peer compatibility with Vite 8 was resolved by npm install. |
| TypeScript | 6.0.3 | Registry version observed; TypeScript ESLint peer range supports <6.1; strict mode enabled. |
| Vitest | 5.1.0 | Registry version observed. |
| PostgreSQL | 17.7-alpine3.22 | Explicit minor image tag for local persistence; production patch digest should be pinned by CI/release process. |
| Redis | 8.4.0-alpine3.22 | Explicit image tag for local persistence; production patch digest should be pinned by CI/release process. |
| Docker Engine | Not available during Phase 4 execution | Docker CLI exists, but the Linux engine named pipe was unavailable; Compose/Testcontainers runtime gates are BLOCKED. |
| Terraform | Not selected for install | No valid Terraform configuration in this phase; binary absent, no IaC claim. |

Package versions are exact in `apps/web/package.json`; Maven parent, plugin versions, Java release, and container tags are explicit in build files. npm lockfile must be generated and committed as part of validation. Maven transitive versions are managed by pinned Spring Boot BOM. Upgrade after compatibility tests and review release/security notes.

## References

- Spring Boot: https://spring.io/projects/spring-boot
- Vite: https://vite.dev/guide/
- Node release status: https://nodejs.org/en/about/previous-releases
- npm package registry (`npm view <package> version`, checked in execution environment).

**Validation:** Phase 4 observed Java 25, Node 24.19.0, npm 11.17.0, Maven 3.9.11, Spring Boot 4.1.2, PostgreSQL 17.7, Redis 8.4.0, React/React DOM 19.3.1, React Router 7.18.5 and Vite 8.3.8. Docker daemon unavailable at validation. Related: [local development](LOCAL_DEVELOPMENT.md), ADR-002.
