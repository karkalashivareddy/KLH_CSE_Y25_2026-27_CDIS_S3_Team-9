# Phase 9: disaster recovery report

**Status:** DR procedures and local PostgreSQL backup/isolated-restore scripts are implemented. Empty-state and populated synthetic catalog/inventory backup/restore drills passed. Hosted run `44264241686` passed the E2E order/payment isolated restore, reconciliation and API smoke step. The latest workflow follow-up additionally uploads the JSON recovery manifest as a CI artifact. Failure injection, dependency recovery and measured RTO/RPO remain outstanding, so Phase 9 acceptance criteria are not met.

**Date:** 2026-11-18
**Branch:** `main` (single-branch workflow; no PRs)
**Base commit:** `54d9a69f3200a9bd4cfa756e77afac9d70230a7d` (latest verified main before this work; hosted CI run [44235127270](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44235127270) passed for this exact SHA)
**Implementation commit:** `20329ba75f2b5e60bfb0847f0221d73d226805ff` (direct push on `main`).
**CI run:** [44240920362](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44240920362) passed at the exact implementation SHA; hosted checks include API integration/package, Terraform/TFLint/Checkov, npm production audit, Gitleaks, OSV, and container scans.

## Scope delivered

- Documented recovery order and classifications for PostgreSQL, RabbitMQ, Redis sessions, images/configuration and observability.
- Added a safe isolated restore runbook, backup policy, failure scenario guide, provisional RTO/RPO, drill evidence record and ADR-038/039.
- Added `infrastructure/scripts/Backup-Postgres.ps1` for a local Compose custom-format dump and SHA-256/JSON metadata, `Restore-Postgres-Isolated.ps1` for checksum verification and isolated restore, and `Get-Recovery-Status.ps1` for read-only schema/data reconciliation counts. Restore requires `-ConfirmRestore`; all three scripts reject non-local environments.
- Added `infrastructure/scripts/Test-PostgresE2ERecovery.sh` for a local/CI-only recovery drill using actual Playwright-created order, payment, inventory, outbox and inbox data. It verifies a custom-format archive checksum, restores to a fresh database, compares source/restore invariants and checks API readiness and catalog access. Workflow run `44255857779` was not retrievable at report time; no passing result is claimed.
- Phase 8 Terraform source configures RDS backup retention and local Compose uses persistent named volumes. Neither is proof of a completed backup/restore exercise.

## Verification evidence

| Check | Status | Evidence |
|---|---|---|
| PowerShell script syntax | PASS | Parser validation passed for all three PowerShell files under Windows PowerShell 5.1 |
| PostgreSQL backup + checksum | PASS (empty and synthetic populated drills) | IDs `drill-20261118-02` and `drill-20261118-05`; hashes and metadata in `docs/07-disaster-recovery/DR_DRILL_RESULTS.md`; artifacts remain local in `%TEMP%` |
| Isolated restore | PASS (empty and synthetic populated drills) | Restored to `recovery_drill_20261118_02` and `recovery_drill_20261118_05`; source application DB was not overwritten |
| Order/payment restore drill | PASS (hosted CI) | Run `44264241686`: the E2E journey created orders and payments; isolated archive restore, source/restore reconciliation, readiness and catalog API smoke all completed successfully. The JSON manifest was printed in the job log; a follow-up workflow retains it as an artifact for inspection. This is a synthetic CI fixture, not a measured RTO/RPO exercise. |
| Flyway/schema, inventory ledger and API restore smoke | PARTIAL PASS | Empty-state API readiness/catalog smoke passed; populated drill recovered 5 Flyway records, 1 product, 1 inventory row, 2 movements, zero inventory invariant/ledger mismatches. A temporary API on the restored DB returned readiness UP and the recovered product. Orders/payments were absent from fixture. |
| Outbox/inbox recovery and event reconciliation | NOT RUN | No controlled recovery drill |
| Redis loss/re-authentication | NOT RUN | Documented only |
| RabbitMQ outage/restart/replay | NOT RUN | Existing messaging tests are not a Phase 9 backup/restore drill |
| Cloud/AZ/region recovery | NOT RUN | No cloud resources provisioned |
| Actual RTO/RPO | NOT MEASURED | No timestamps or data-loss evidence |

## Limitations

The drills exposed and fixed database selection, user selection, and PowerShell 5.1 UTF-8 metadata issues. A guarded `-SourceDatabase drtest_*` option supports disposable local fixtures only. The populated drill did not include order/payment data; outbox event reconciliation, Redis/RabbitMQ failure drills, and measured RTO/RPO remain unverified. Compose volumes are not backups. Terraform RDS settings remain unprovisioned. Four-hour RTO and 15-minute RPO are unapproved targets. No cloud or destructive operation was executed.
