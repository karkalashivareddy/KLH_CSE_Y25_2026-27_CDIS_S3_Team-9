# Phase 10: Portability, FinOps, security and final validation report

**Status:** documentation groundwork added; final engineering validation and closure remain pending. This report does not claim Phase 10 acceptance.

**Date:** 2026-11-18
**Branch:** `main` (repository owner requested one branch, direct pushes, and no PRs)
**Base SHA:** `54d9a69f3200a9bd4cfa756e77afac9d70230a7d` (latest verified main commit before this work; hosted CI [44235127270](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44235127270) passed at this SHA)
**Implementation SHA:** `e47724aa1aca6319bdb347c689e57c7472b45cd5` (direct push to `main`; phase acceptance remains in progress)
**Latest completed CI run:** [44245526649](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44245526649) passed at the exact implementation SHA.

## Documentation delivered in this work

- Capability-based AWS/Azure/Google Cloud mapping and semantic portability limits: `docs/03-architecture/MULTI_CLOUD_PORTABILITY.md`.
- Cost allocation, scenario assumptions and review cycle: `docs/08-finops/FINOPS_OPERATING_MODEL.md`.
- Capability-based, dated official pricing comparison and reproducible estimate worksheet: `docs/08-finops/MULTI_CLOUD_COST_COMPARISON.md`.
- Source/CI-based security review and remaining threat validation: `docs/05-security/FINAL_SECURITY_REVIEW.md`.
- Evidence snapshot with PASS and NOT VERIFIED statuses: `docs/09-testing/FINAL_VALIDATION_MATRIX.md`.
- Playwright shopper and checkout browser journeys: two happy paths passed locally (2/2) against the Compose API and synthetic stock, using cached Chromium 1248 via an explicit executable-path override because the pinned browser download did not complete. Added a repeatable SQL fixture, local PowerShell wrapper, insufficient-stock failure test, and hosted Compose-backed E2E job. PowerShell parsing and three-test discovery pass; the new failure case and hosted job remain NOT VERIFIED. The latest local full run could not reach the API after Docker Desktop returned an API 500. The earlier successful E2E used equivalent synthetic product/stock inserted directly into the local database.

## Evidence and limitations

- AWS is the primary implementation target. Azure and Google Cloud have no Terraform, adapters, deployments or failover tests in this repository.
- Phase 8 CI run [44108903172](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44108903172) passed at exact commit `1fe7b1308958b974758b069abbced544e48ac5c3`.
- Run 44231664015 at `08fe748707723a12be85f17bc4fe1525a0f165cf` failed web `npm audit`: 12 high severity findings through `picomatch` (GHSA-fc5q-39xc-vjpf), with no patched version listed in the advisory. This is an unresolved security gate; security validation is not passed.
- Vite is now classified as a development dependency and the development server binds only to loopback. Local and hosted production dependency audits passed. The dev-toolchain advisory remains open until upstream publishes a patched release; this mitigation does not make the high-severity finding disappear, and Phase 10 remains incomplete.
- Phase 7 includes a local catalog-only k6 result. It does not establish cloud performance or capacity for checkout/consumers.
- No approved region and measured workload were available for a reproducible monthly cost estimate. No actual billed costs or savings were measured.
- This security review is not DAST, penetration testing, or a formal compliance audit. DAST and failure-path E2E are not run and E2E is not yet part of hosted CI.
- Phase 9 populated restore covers synthetic catalog/inventory only; orders/payments, dependency failure drills, and actual RTO/RPO measurements remain outstanding.
- No cloud resources were provisioned. No real cloud recovery exercise occurred.

## Phase 10 acceptance status

| Requirement | Status |
|---|---|
| Multi-cloud mapping and semantic differences | DOCUMENTED; provider adapters and tests NOT IMPLEMENTED |
| Sourced reproducible provider cost comparison | Method, source links and worksheet documented; monthly totals NOT ESTIMATED because region/workload inputs and calculator exports are absent |
| Security review | CI scans and production dependency audit PASS on run `44245526649` at `e47724a`; unpatched high severity dev-toolchain advisory remains OPEN; DAST NOT RUN |
| E2E journeys | Shopper and simulated checkout success journeys PASS locally (2/2); insufficient-stock case and hosted E2E job added but NOT VERIFIED |
| Recovery drill evidence | Populated synthetic catalog/inventory restore PASS; order/payment restore, dependency failure drills and RTO/RPO remain pending |
| Final documentation matches implementation | PARTIAL; E2E failure journeys, order/payment recovery, measured recovery targets, DAST, and final project handoff remain |
| CI on current implementation SHA | PASS, run `44245526649` at `e47724a`; phase closure is still pending remaining acceptance items |

Do not mark this phase complete until E2E failure journeys and hosted browser verification, Phase 9 order-data and dependency recovery evidence, measured RTO/RPO, cost inputs and estimate provenance, DAST/security closure, final project handoff, and final main/CI state are verified. The unpatched high severity development-toolchain advisory remains an open finding; the production dependency audit passes but this mitigation does not resolve that advisory.
