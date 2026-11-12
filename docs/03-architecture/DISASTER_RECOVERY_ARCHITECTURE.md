# Disaster recovery architecture

**Purpose/scope:** target recovery workflow; not implemented or exercised. Proposed RTO/RPO in DR-001 (4 h/15 min) require stakeholder approval and feasibility/cost testing.

```mermaid
flowchart TD
  Detect[Alert / incident] --> Declare[Authorized incident declaration]
  Declare --> Assess[Classify impact, scope, RTO/RPO]
  Assess --> Contain[Contain threat / stop unsafe writes]
  Contain --> Select[Choose approved recovery point and region]
  Select --> Restore[Restore isolated data and services]
  Restore --> Verify[Integrity, security, smoke, reconciliation checks]
  Verify --> Switch{Approver accepts?}
  Switch -->|Yes| Traffic[Controlled traffic shift]
  Switch -->|No| Rollback[Rollback / continue investigation]
  Traffic --> Observe[Observe, reconcile, close incident]
  Observe --> Review[Evidence, measured RTO/RPO, lessons]
```

Recover from immutable encrypted backups and documented infrastructure, not untested snapshots alone. Define recovery roles, separation of duties, backup retention, keys, provider dependencies, data loss communication, and isolated restore tests. Validate last known-good point, integrity, application compatibility and authorized cutover. Abuse risks include ransomware deletion of backups, forged recovery requests, and stale-data rollback; use MFA, immutable retention, scoped roles and dual approval. No recovery has been performed; target architecture alone does not meet RTO/RPO.
