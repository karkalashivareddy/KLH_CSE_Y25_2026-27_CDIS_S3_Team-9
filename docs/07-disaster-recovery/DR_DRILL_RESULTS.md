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
| Application smoke test | PASS; temporary API container against restored DB returned readiness `UP` and public `/api/v1/products` returned HTTP 200 with an empty page |
| Procedure interval | 17.8 seconds from backup metadata timestamp to restore validation timestamp; this is not measured RTO |
| Data gap / RPO | Not measured; source had no business records and no failure point was injected |
| Limitations | Empty customer dataset; no customer/order data flow, inventory/order replay, Redis/RabbitMQ drills or cloud failover tested |

| Drill | Status | Result |
|---|---|---|
| PostgreSQL backup creation and checksum verification | PASS (local empty-state drill) | Drill `drill-20261118-02`; SHA-256 recorded above |
| Isolated PostgreSQL restore | PASS (local empty-state drill) | Restored to a new `recovery_*` database without overwriting source |
| Flyway and business consistency checks after restore | PARTIAL | 5 migration records and inventory/payment constraints queried; no business rows existed to validate |
| Outbox/inbox and RabbitMQ recovery/replay | NOT RUN | No controlled replay exercise evidence |
| Redis loss and re-authentication | NOT RUN | Designed behavior; no Phase 9 failure drill evidence |
| Cloud/AZ/region failover | NOT RUN | No cloud resources have been provisioned |

No measured RTO, RPO, or data-loss window is available. A local empty-state archive was verified and restored; this is not evidence of business-data recovery or cloud failover.

### Local populated catalog/inventory restore — 2026-11-18

| Field | Evidence |
|---|---|
| Exercise ID | `drill-20261118-05` |
| Environment | Local Docker Compose; PostgreSQL 17.7 |
| Source | Disposable synthetic database `drtest_20261118_01`; application database was not modified |
| Backup artifact | `%TEMP%\cloudresilience-drill\drill-20261118-05.dump` (local-only, not committed) |
| SHA-256 | `77714e9355845c5d20214addf86da58f69f4e22573502efc6906e0881e22803d` |
| Archive | Custom format, 71,332 bytes; JSON metadata names source DB and PostgreSQL version |
| Restore target | New isolated database `recovery_drill_20261118_05` |
| Restore result | PASS; checksum verified and restore completed |
| Application smoke test | PASS; temporary API container against the restored DB returned readiness HTTP 200/UP and `/api/v1/products` HTTP 200 with the recovered synthetic product and available quantity 15 |
| Recovered counts | 5 successful Flyway migrations; 1 product; 1 inventory row; 2 inventory movements; 1 reserved row |
| Reconciliation | 0 inventory invariant violations; 0 inventory ledger mismatches; 0 missing order payments; outbox backlog 0; quarantined DLQ 0 |
| Recovery timing | NOT MEASURED as RTO/RPO; this is a fixture restore without injected outage |
| Limitations | Fixture contains no customer/order/payment business records; application checkout against restored fixture, Redis loss, RabbitMQ outage/replay, and cloud failover were not tested |

This drill demonstrates populated catalog and inventory archive recovery only. It does not satisfy the full Phase 9 acceptance criteria.

### Hosted order/payment restore with browser E2E data — 2026-11-18

| Field | Evidence |
|---|---|
| Workflow | [GitHub Actions run 44265982978](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44265982978), commit `05295f203f08437d0bde68951934cc9097ded2e1` |
| Exercise | `ci-order-recovery`; Compose PostgreSQL 17.7; E2E-created order and payment |
| Archive | `pg_dump` custom format, 73,893 bytes; SHA-256 `ab7b5224aaa301f017cea6100dc8e486e219d0f69bee75760ef83068ecac2003` |
| Recovery target | New isolated database `recovery_e2e_44265982978_1`; source database preserved |
| Restored business rows | 1 order, 1 order item, 1 payment, 1 reservation, 1 product/inventory row, 2 inventory movements |
| Event state | 1 outbox event, 2 processed-event inbox rows, 1 notification, 1 order activity, 0 unsettled outbox events, 0 DLQ rows |
| Consistency checks | Source and restored snapshots identical; 5 successful Flyway records; zero missing/mismatched payments, item subtotal mismatches, inventory invariant/ledger mismatches, outbox aggregate mismatches, or unprocessed events |
| Application smoke | PASS; API readiness and recovered catalog endpoint passed |
| Procedure duration | 11 seconds for this synthetic archive/restore/API validation interval; not a measured RTO |
| RPO/data loss | NOT MEASURED; no failure point or data-loss window was injected; manifest `actualRtoSeconds` and `actualRpo` are null |
| Artifact | `postgres-recovery-44265982978` CI artifact contains the JSON manifest and custom-format archive; not committed to the repository |

This drill verifies restoration of a populated synthetic order/payment database in CI. It does not test production recovery, broker outage/replay, Redis loss, cloud failover, or an approved RTO/RPO target.

## Evidence record for future drills

For each authorized exercise, attach a sanitized evidence reference and record: exercise ID, environment, commit/image SHA, backup ID/checksum, recovery point UTC, failure time, detection time, recovery start, API readiness time, verification-complete time, actual RTO, actual RPO/data gap, integrity checks, skipped/failed checks, approver, cleanup confirmation, deviations and follow-up owner/date. Never include secrets, credentials, cookies or real customer data in the evidence package.
