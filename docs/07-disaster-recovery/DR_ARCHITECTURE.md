# Disaster recovery architecture

**Status (2026-11-18):** recovery design and local PostgreSQL backup/isolated-restore tooling are implemented; scripts have PowerShell syntax validation only. Backup/restore scripts and consistency reporting have not been executed against Docker/PostgreSQL. Phase 8 Terraform defines RDS automated backups with seven-day retention, but no cloud resources or recovery drill exist.

## Data classification and recovery order

| Component | Authority / classification | Recovery behavior |
|---|---|---|
| PostgreSQL identity, catalog, inventory, carts, orders, payments, reservations, ledger, outbox, inbox | Critical durable source of truth | Restore a consistent backup or PITR point into isolation; validate invariants before application traffic |
| RabbitMQ | Durable asynchronous transport; downstream of PostgreSQL | Restore broker persistence if available, then reconcile against outbox/inbox; republish only through controlled idempotent processing |
| Redis | Shared HTTP session cache; not business authority | Losing it invalidates sessions and requires re-authentication; carts and orders remain in PostgreSQL |
| Application images and Terraform | Reproducible deployment artifacts | Redeploy exact immutable image SHA and reviewed infrastructure source |
| Observability volumes | Operational evidence | Retain independently where required; telemetry loss must not change business recovery correctness |
| Secrets and encryption keys | Recovery dependency | Restore access through authorized secret/key systems; never include plaintext credentials in backups |

## Recovery sequence

1. Declare an incident, identify the affected environment, preserve evidence, stop unsafe writes, and record last-known-good time.
2. Establish an isolated recovery environment and verify access to the selected backup and encryption keys.
3. Restore PostgreSQL first. Verify Flyway history and schema before starting the API.
4. Run identity, catalog, inventory, order/payment/reservation, ledger, outbox and inbox consistency checks.
5. Restore or recreate RabbitMQ. Keep consumers stopped until topology and database state are ready.
6. Start the API with publisher/consumer controls chosen for the scenario. Inspect pending outbox events and broker deliveries; do not replay manually without an audited plan.
7. Start consumers and reconcile idempotently. Confirm no duplicate inventory movement, payment transition, or notification effect.
8. Redis may be recreated empty; confirm users can authenticate again and durable cart/order state remains available.
9. Run synthetic customer/admin checks, inspect logs/traces/alerts, obtain independent data-owner approval, then perform an explicitly authorized cutover.
10. Record actual timestamps, data-loss window, decisions, integrity evidence, deviations and remediation. No cutover occurs during an unapproved exercise.

## Recovery boundaries

The outbox is in the same PostgreSQL transaction as its business change. Restoring PostgreSQL therefore restores both committed business state and the corresponding pending event record. RabbitMQ and PostgreSQL are not a distributed transaction; duplicates are expected and inbox uniqueness/idempotent handlers are required. A broker backup alone is not authoritative. A Redis loss is a session outage, not a cart/order restore event.

The Phase 8 Terraform target uses RDS PostgreSQL, ElastiCache Redis OSS and Amazon MQ RabbitMQ, but it has not been applied. Region failover, cross-region backup replication, provider failover, and cloud recovery have not been tested.
