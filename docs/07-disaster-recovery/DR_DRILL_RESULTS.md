# Disaster recovery drill results

**Reporting date:** 2026-11-18

## Current evidence

### Local isolated PostgreSQL restore — 2026-11-18

| Field | Evidence |
|---|---|
| Exercise ID | `drill-20261118-02` |
| Environment | Local Docker Compose; PostgreSQL 17.7 |
| Backup artifact | `%TEMP%\cloudresilience-drill\drill-20261118-02.dump` (local-only, not committed) |
| SHA-256 | `0c6dedb37c263a709e7a0fe33f1f27d7210f892cf86cbd3ed22638e410d49b2b` |
| Backup metadata | DB `cloudresilience`, version `17.7`, custom format, 70,790 bytes |
| Restore target | New isolated DB `recovery_drill_20261118_02` |
| Restore result | PASS; checksum verified; `pg_restore` completed; 22 public schema tables |
| Flyway history | 5 successful migrations in both source and restored DB |
| Source/restored counts | products 0/0; orders 0/0; payments 0/0; inventory rows 0/0 |
| Recovery status checks | inventory invariant violations 0; missing payments 0; outbox backlog 0; quarantined DLQ 0 |
| Procedure interval | 17.8 seconds from backup metadata timestamp to restore validation timestamp; this is not measured RTO |
| Data gap / RPO | Not measured; source had no business records and no failure point was injected |
| Limitations | Empty customer dataset; API against restore, inventory/order replay, Redis/RabbitMQ drills and cloud failover not tested |

| Drill | Status | Result |
|---|---|---|
| PostgreSQL backup creation and checksum verification | PASS (local empty-state drill) | Drill `drill-20261118-02`; SHA-256 recorded above |
| Isolated PostgreSQL restore | PASS (local empty-state drill) | Restored to a new `recovery_*` database without overwriting source |
| Flyway and business consistency checks after restore | PARTIAL | 5 migration records and inventory/payment constraints queried; no business rows existed to validate |
| Outbox/inbox and RabbitMQ recovery/replay | NOT RUN | No controlled replay exercise evidence |
| Redis loss and re-authentication | NOT RUN | Designed behavior; no Phase 9 failure drill evidence |
| Cloud/AZ/region failover | NOT RUN | No cloud resources have been provisioned |

No measured RTO, RPO, or data-loss window is available. A local empty-state archive was verified and restored; this is not evidence of business-data recovery or cloud failover.

## Evidence record for future drills

For each authorized exercise, attach a sanitized evidence reference and record: exercise ID, environment, commit/image SHA, backup ID/checksum, recovery point UTC, failure time, detection time, recovery start, API readiness time, verification-complete time, actual RTO, actual RPO/data gap, integrity checks, skipped/failed checks, approver, cleanup confirmation, deviations and follow-up owner/date. Never include secrets, credentials, cookies or real customer data in the evidence package.
