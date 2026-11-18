# Phase 9: disaster recovery report

**Status:** DR procedures and local PostgreSQL backup/isolated-restore scripts are implemented. One local empty-state backup/checksum/restore drill passed; business-data recovery, failure injection, dependency recovery and measured RTO/RPO remain outstanding, so Phase 9 acceptance criteria are not met.

**Date:** 2026-11-18
**Branch:** `main` (single-branch workflow; no PRs)
**Base commit:** `1fe7b1308958b974758b069abbced544e48ac5c3` (Phase 8 integrated; hosted CI run [44108903172](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44108903172) passed for this exact SHA)
**Implementation commit:** pending
**CI run:** [44231664015](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44231664015) on `08fe748707723a12be85f17bc4fe1525a0f165cf`; API, Terraform, secret and Maven scan jobs passed; frontend job failed npm audit due to a high severity picomatch advisory with no patched version listed.

## Scope delivered

- Documented recovery order and classifications for PostgreSQL, RabbitMQ, Redis sessions, images/configuration and observability.
- Added a safe isolated restore runbook, backup policy, failure scenario guide, provisional RTO/RPO, drill evidence record and ADR-038/039.
- Added `infrastructure/scripts/Backup-Postgres.ps1` for a local Compose custom-format dump and SHA-256/JSON metadata, `Restore-Postgres-Isolated.ps1` for checksum verification and isolated restore, and `Get-Recovery-Status.ps1` for read-only schema/data reconciliation counts. Restore requires `-ConfirmRestore`; all three scripts reject non-local environments.
- Phase 8 Terraform source configures RDS backup retention and local Compose uses persistent named volumes. Neither is proof of a completed backup/restore exercise.

## Verification evidence

| Check | Status | Evidence |
|---|---|---|
| PowerShell script syntax | PASS | Parser validation passed for all three PowerShell files |
| PostgreSQL backup + checksum | PASS (empty-state drill) | Backup `drill-20261118-02`; SHA-256 and metadata in `docs/07-disaster-recovery/DR_DRILL_RESULTS.md`; artifact remains local in `%TEMP%` |
| Isolated restore | PASS (empty-state drill) | Restored to `recovery_drill_20261118_02`; source was not overwritten |
| Flyway/schema and inventory/order consistency after restore | PARTIAL | 5 migrations, 22 tables, zero inventory invariant violations; no order/inventory data existed |
| Outbox/inbox recovery and event reconciliation | NOT RUN | No controlled recovery drill |
| Redis loss/re-authentication | NOT RUN | Documented only |
| RabbitMQ outage/restart/replay | NOT RUN | Existing messaging tests are not a Phase 9 backup/restore drill |
| Cloud/AZ/region recovery | NOT RUN | No cloud resources provisioned |
| Actual RTO/RPO | NOT MEASURED | No timestamps or data-loss evidence |

## Limitations

The initial drill exposed and fixed database selection, user selection, and PowerShell 5.1 UTF-8 metadata issues. The successful run covers an empty database only; a populated order/inventory restore, API validation, outbox reconciliation, Redis/RabbitMQ failure drills, and measured RTO/RPO remain unverified. Compose volumes are not backups. Terraform RDS settings remain unprovisioned. Four-hour RTO and 15-minute RPO are unapproved targets. No cloud or destructive operation was executed.
