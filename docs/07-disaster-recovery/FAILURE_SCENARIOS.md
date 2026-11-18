# Failure scenarios and response

| Scenario | Immediate response | Recovery and validation |
|---|---|---|
| PostgreSQL unavailable | Stop checkout/write retries that could create unsafe side effects; preserve API and database logs | Restore service or isolated backup/PITR; verify schema, inventory, reservations and order/payment state before accepting writes |
| Redis/session store lost | Treat existing sessions as invalid; do not attempt to reconstruct identity from cache | Recreate Redis; require re-authentication; confirm carts and orders from PostgreSQL |
| RabbitMQ unavailable | Keep checkout database transactions authoritative; alert on outbox age and broker health | Restore broker connectivity; allow outbox publisher to resume; monitor confirms, retries, duplicates and DLQ |
| API deployment fails | Stop rollout and use deployment rollback to a compatible immutable image | Check database migration compatibility, readiness and error rate; do not reverse destructive migrations automatically |
| Publisher crashes after broker confirm | Expect possible duplicate delivery because DB publish mark may be missing | Lease expiry permits retry; consumer inbox/idempotency must make duplicate harmless |
| Consumer crashes before DB commit | Broker message remains unacknowledged and may be redelivered | Verify rollback and redelivery; acknowledge only after durable work commits |
| Consumer crashes after commit, before ack | Duplicate delivery is expected | Inbox unique key suppresses repeated logical effect; verify outcome and retry metrics |
| Poison event / DLQ grows | Preserve message metadata and avoid infinite retry | Inspect schema/version/error category; controlled, audited replay only after correction |
| Backup checksum/restore verification fails | Quarantine artifact and stop recovery using it | Select another verified recovery point; investigate backup pipeline and key access |
| Disk/storage pressure | Protect PostgreSQL and queue storage; stop nonessential ingestion if needed | Expand/repair using approved steps; verify WAL, database and broker state before resume |
| Region outage | Declare region impact; use only an approved alternate-region plan | Current project has no provisioned cross-region failover; restore design is untested in cloud, so do not claim automatic failover |
| Credential compromise | Revoke/rotate affected identities and contain access | Audit access, preserve evidence, rotate database/broker/secrets and verify backup/restore permissions |

Failure injection against real cloud infrastructure is not authorized. Local drills must be isolated, reversible and recorded. No Phase 9 failure drill has been run as of this report.
