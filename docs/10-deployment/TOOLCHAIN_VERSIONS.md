# Toolchain versions

**Purpose/scope:** pinned foundation versions for reproducible local builds; not a claim that every tool is installed. **Date checked:** 2026-11-12. **Assumptions:** official project pages and package registry metadata reflect current releases; refresh before upgrading.

| Component | Selected version | Rationale / evidence |
|---|---:|---|
| Java | 25 LTS | Supplied project baseline; local runtime verified as Java 25 (build 25+37). |
| Spring Boot | 4.1.2 | Official Spring Boot project page listed 4.1.2; Java 25 compatible baseline. Verify starter/dependency changes when compiling. |
| Maven | 3.9.11 (container) / 3.9.9+ | Pinned build image and practical minimum; local `mvn` absent at audit time. |
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
| Docker Engine | 29.7.2 observed | Local tooling observation only. Compose plugin version still needs verification. |
| Terraform | Not selected for install | No valid Terraform configuration in this phase; binary absent, no IaC claim. |

Package versions are exact in `apps/web/package.json`; Maven parent, plugin versions, Java release, and container tags are explicit in build files. npm lockfile must be generated and committed as part of validation. Maven transitive versions are managed by pinned Spring Boot BOM. Upgrade after compatibility tests and review release/security notes.

## References

- Spring Boot: https://spring.io/projects/spring-boot
- Vite: https://vite.dev/guide/
- Node release status: https://nodejs.org/en/about/previous-releases
- npm package registry (`npm view <package> version`, checked in execution environment).

**Validation:** local `java`, Node, npm and Docker version commands ran; Maven and Terraform commands were not found. Related: [local development](LOCAL_DEVELOPMENT.md), ADR-002.
