# Cost management strategy

**Purpose/scope:** prevent accidental spend and define future cost evidence. Phase 1 is local; no cloud resources are authorized or deployed.

- Maintain resource inventory with owner, purpose, environment, provider, region, sensitivity, creation date and deletion date.
- Require written estimate, budget alert, approval and teardown steps before each cloud environment; tag resources consistently.
- Separate sandbox from production, use quotas and least privilege, default to small/ephemeral workloads, stop idle compute, expire test data, and review orphaned resources weekly.
- Estimate compute hours/concurrency, database sizing/HA/backups, object storage/requests, queue volume, egress, NAT, logs/metrics/traces, snapshots, support and taxes under explicit region/usage assumptions.
- Compare equivalent service level, regions, retention, network and support assumptions across providers. Do not call services “free”; free tiers/credits vary by eligibility, time, region and overage.
- Record estimate vs actual, currency, period, source timestamp, and allocation method. Avoid fabricated dashboard values.
- Cost alerts are not spending caps. Document response and resource cleanup before deployment; recovery resources and retained backups also incur cost.

Validation: owner review of estimate and cleanup checklist; later billing export reconciliation. Dependencies: cloud account approval, region, workload and budget decisions. Related: cloud mapping, deployment architecture.
