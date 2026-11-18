# RTO and RPO targets

**Status:** provisional academic targets only; not approved or measured. Prior design proposed four-hour RTO and 15-minute RPO; these values are retained as hypotheses for stakeholder review, not service commitments.

| Service/data | Provisional RTO | Provisional RPO | Evidence status |
|---|---:|---:|---|
| PostgreSQL business data and application | 4 hours | 15 minutes | NOT MEASURED; no restore drill or deployed cloud system |
| RabbitMQ downstream processing | 4 hours to restore processing | 15 minutes for broker-only state, with PostgreSQL outbox reconciliation | NOT MEASURED; no broker backup/restore drill |
| Redis sessions | 1 hour to recreate store; users re-authenticate | Session loss accepted | NOT MEASURED; behavior documented only |
| Observability | 24 hours | 24 hours | Proposed operational evidence target; NOT MEASURED |

These targets require owner approval, explicit business impact, workload, backup cadence, region and cost assumptions. The RPO is the maximum acceptable committed-data loss window; database backup cadence/PITR availability must support it. The RTO includes detection, decision, restore, integrity checks and service validation, not merely database startup.

During a drill record: failure start, detection, declaration, recovery start, database service restored, application restored, integrity verification complete, last included transaction time, recovery point time, actual data gap, actual RTO and actual RPO. Calculate actual RTO from incident start through approved service restoration; calculate actual RPO from the failure cutoff to the newest recovered committed data. Mark the result NOT MEASURED if timestamps or data markers are missing.
