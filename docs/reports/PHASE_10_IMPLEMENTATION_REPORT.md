# Phase 10: Portability, FinOps, security and final validation report

**Status:** documentation groundwork added; final engineering validation and closure remain pending. This report does not claim Phase 10 acceptance.

**Date:** 2026-11-18
**Branch:** `main` (repository owner requested one branch, direct pushes, and no PRs)
**Base SHA:** `54d9a69f3200a9bd4cfa756e77afac9d70230a7d` (latest verified main commit before this work; hosted CI [44235127270](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44235127270) passed at this SHA)
**Implementation SHA:** `dd7560dd3854bc6675bbe0f8919af7c4d2774a56` (direct push to `main`; phase acceptance remains in progress)
**Latest completed CI run:** [44249204505](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44249204505) passed at the exact implementation SHA; all six jobs including browser E2E passed.
**Latest code follow-up before current change:** commit `18042b632427e85d22a00aa2f21caef1067fd296` split API/E2E startup from the deployable web container and added header verification, order/payment recovery and passive ZAP. Run `44264241686` on documentation-updated `82cb5c7` passed all 3 browser journeys and the order/payment restore step, but web-container startup failed before headers/DAST. The current follow-up runs the built image directly on the isolated Compose network and retains recovery evidence.
Run `44265982978` at `05295f203f08437d0bde68951934cc9097ded2e1` passed E2E (3/3), the order/payment restore drill, recovery API smoke, web-image build/start and the five other CI jobs. The host-published response probe returned no headers, so DAST was skipped. The artifact from run `44273350323` was empty. The current follow-up checks container loopback instead of publishing the container port and retains diagnostic headers.

## Documentation delivered in this work

- Capability-based AWS/Azure/Google Cloud mapping and semantic portability limits: `docs/03-architecture/MULTI_CLOUD_PORTABILITY.md`.
- Cost allocation, scenario assumptions and review cycle: `docs/08-finops/FINOPS_OPERATING_MODEL.md`.
- Capability-based, dated official pricing comparison and reproducible estimate worksheet: `docs/08-finops/MULTI_CLOUD_COST_COMPARISON.md`.
- Source/CI-based security review and remaining threat validation: `docs/05-security/FINAL_SECURITY_REVIEW.md`.
- Evidence snapshot with PASS and NOT VERIFIED statuses: `docs/09-testing/FINAL_VALIDATION_MATRIX.md`.
- Playwright shopper, simulated-checkout, and insufficient-stock journeys: hosted E2E passed 3/3 in run `44249204505` against a Compose API and synthetic stock fixture. Local happy paths passed 2/2 using cached Chromium 1248 via an explicit executable-path override because the pinned browser download did not complete. Initial hosted run `44248462845` failed because seeding raced Flyway table creation; adding `docker compose up --wait` fixed the ordering. The local PowerShell fixture wrapper passed parsing; hosted CI executed the shared SQL fixture successfully. Current local full rerun cannot reach the API while Docker Desktop returns an API 500.
- CI run `44253328788` passed web, API, Terraform/IaC, Gitleaks and OSV but failed during Compose startup before browser journeys, restore or DAST. Run `44264241686` passed E2E and recovery but failed starting the web container. Run `44265982978` then passed E2E 3/3, order/payment backup/restore/API smoke, web image build/start and all five non-browser jobs; the host-published header probe returned no response and ZAP did not run. Its recovery JSON/archive artifact was downloaded and inspected. Run `44273350323` retained an empty web-header artifact. The current follow-up probes the web process through container loopback.

## Evidence and limitations

- AWS is the primary implementation target. Azure and Google Cloud have no Terraform, adapters, deployments or failover tests in this repository.
- Phase 8 CI run [44108903172](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44108903172) passed at exact commit `1fe7b1308958b974758b069abbced544e48ac5c3`.
- Run 44231664015 at `08fe748707723a12be85f17bc4fe1525a0f165cf` failed web `npm audit`: 12 high severity findings through `picomatch` (GHSA-fc5q-39xc-vjpf), with no patched version listed in the advisory. This is an unresolved security gate; security validation is not passed.
- Vite is now classified as a development dependency and the development server binds only to loopback. Local and hosted production dependency audits passed. The dev-toolchain advisory remains open until upstream publishes a patched release; this mitigation does not make the high-severity finding disappear, and Phase 10 remains incomplete.
- Phase 7 includes a local catalog-only k6 result. It does not establish cloud performance or capacity for checkout/consumers.
- No approved region and measured workload were available for a reproducible monthly cost estimate. No actual billed costs or savings were measured.
- This security review is not penetration testing or a formal compliance audit. The web container started in run `44265982978`, but a response-header assertion failed before passive ZAP; both validations remain open. Hosted E2E passed shopper success, simulated checkout success, and insufficient-stock rejection; payment-decline, duplicate-checkout and admin lifecycle journeys are not covered.
- Phase 9 local restore covers synthetic catalog/inventory, and hosted run `44264241686` passed isolated restore of Playwright-created orders/payments. Dependency failure drills and actual RTO/RPO measurements remain outstanding.
- No cloud resources were provisioned. No real cloud recovery exercise occurred.

## Phase 10 acceptance status

| Requirement | Status |
|---|---|
| Multi-cloud mapping and semantic differences | DOCUMENTED; provider adapters and tests NOT IMPLEMENTED |
| Sourced reproducible provider cost comparison | Method, source links and worksheet documented; monthly totals NOT ESTIMATED because region/workload inputs and calculator exports are absent |
| Security review | CI scans and production dependency audit PASS on run `44265982978`; unpatched high severity dev-toolchain advisory remains OPEN; header assertion FAILED and passive ZAP NOT RUN |
| E2E journeys | Shopper, simulated checkout, and insufficient-stock journeys PASS in hosted CI (3/3, 0 skipped) |
| Recovery drill evidence | Populated synthetic catalog/inventory restore PASS; hosted order/payment restore PASS in `44264241686`; dependency failure drills and RTO/RPO remain pending |
| Final documentation matches implementation | PARTIAL; payment-decline, duplicate-checkout and admin lifecycle journeys, dependency failure drills, measured recovery targets and security finding resolution remain |
| CI on current implementation SHA | Run `44265982978`: 5/6 jobs passed; Browser E2E, checkout, order/payment restore and web image startup passed, header assertion failed before DAST |

Do not mark this phase complete until E2E failure journeys and hosted browser verification, Phase 9 order-data and dependency recovery evidence, measured RTO/RPO, cost inputs and estimate provenance, DAST/security closure, final project handoff, and final main/CI state are verified. The unpatched high severity development-toolchain advisory remains an open finding; the production dependency audit passes but this mitigation does not resolve that advisory.
