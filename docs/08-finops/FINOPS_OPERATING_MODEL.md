# FinOps operating model

**Status:** cost governance and estimate inputs documented. No provider has been deployed for this project, no billed-cost data exists, and no reproducible monthly estimate has been approved.

## Principles and ownership

- Tag every provisioned resource with `Project=CloudResilience`, `Environment`, `Owner`, and `ManagedBy=Terraform`; add `CostCenter` when an owner defines one.
- Assign a named environment owner to approve sizing, retention, budgets and teardown. Review spend and untagged resources monthly after deployment.
- Distinguish design estimates, calculator estimates, invoices and measured utilization. Never present estimates as actual bills or savings.
- Set budget alerts before deployment; alerts notify, they do not cap spending. Do not assume free-tier eligibility or promotional credits.

## Cost inventory

| Cost area | Key drivers | Control / review |
|---|---|---|
| Compute | ECS task vCPU/memory/runtime, desired count, ALB hours/LCUs | Use measured Phase 7 workload; schedule demo environments off when idle; bound autoscaling. |
| Data | PostgreSQL class/storage/I/O/Multi-AZ/PITR, Redis nodes/replicas, Amazon MQ class/deployment/storage | Select from tested RTO/RPO; development single-node defaults are not HA. Revisit backups and failover cost. |
| Network | NAT gateways and bytes, interface endpoints, cross-AZ/inter-region transfer, public egress | Compare single and per-AZ NAT against failure tolerance; keep endpoints only where justified. |
| Logs/metrics/traces | Ingestion, retention, dashboards, WAF/ALB/VPC flow logs | Set bounded retention and sampling; preserve only required recovery/security evidence. |
| Storage and DR | S3 log retention/versioning, snapshots, cross-region copies, image layers | Set lifecycle and retention; cross-region recovery has both storage and transfer cost. |
| Security | KMS key usage, Secrets Manager secrets/API, WAF rules/requests | Inventory keys/secrets/rules; budget for operational control, not only compute. |

## Comparable estimate scenarios

Produce three estimates with the same region, currency, date and workload assumptions for AWS, Azure and Google Cloud:

1. **Academic/demo:** short scheduled runtime, minimum validated capacity, explicitly non-HA where approved, test data and defined cleanup.
2. **Staging:** recurring integration and demo activity, retained state, tested restore, bounded autoscaling.
3. **Production-like HA:** measured peak load, cross-zone application/data resilience, defined backup/PITR, observability and support assumptions.

For each, record exact service/SKU/engine, region, hours/month, vCPU and memory, replicas, storage and IOPS, requests/traffic, network egress and cross-AZ traffic, log/trace retention, backup retention, support/tax exclusions, discounts, calculator export/link, and date checked. If any input is unknown, mark the result **NOT ESTIMATED** rather than supplying a speculative number.

## Review cycle

- Before deployment: review provider calculator totals, cost allocation, budgets, retention, NAT, HA premium, and DR copy cost.
- Weekly during a short demo: check resource state and alerts; stop only approved non-production resources while preserving requested evidence/data.
- Monthly while active: compare bill/Cost and Usage report to estimates, explain variance by service and owner, and adjust only after application performance/recovery needs are met.
- After changes: update estimate for task counts, database sizing, log retention, traffic or topology.

## Current evidence

- Phase 8 includes small development defaults, tags, configurable counts/storage/retention and no cloud apply. It has not been costed with an approved region/workload.
- Phase 7 provides one local five-VU, 30-second catalog run; it does not model checkout, cloud egress, production traffic, DB saturation or observed cloud utilization.
- There are no cloud invoices, Cost Explorer/Cost Management exports, deployed-resource inventory, utilization measurements, or cost-savings baseline.
- Therefore monthly cost, actual cost, savings percentage and multi-cloud cost ranking are **NOT RUN / NOT VERIFIED**.

Related estimate input checklist: [AWS cost estimation](AWS_COST_ESTIMATION.md). Rebuild quotes from the provider's official calculator when the environment, region and usage profile are approved.
