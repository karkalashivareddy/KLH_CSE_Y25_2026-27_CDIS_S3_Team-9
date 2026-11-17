# AWS cost estimation

**Pricing review date:** 2026-11-17. **Status:** no numeric monthly estimate is published yet. The repository has no approved AWS region, measured workload, account discounts, or selected service sizes, and no pricing-calculator export is attached. Inventing a monthly total would be misleading. No AWS resources were provisioned and no billed usage was measured.

## Scenario assumptions to finalize

| Scenario | Workload target | Availability intent | Estimate status |
|---|---|---|---|
| Academic/demo | Short scheduled demonstrations; minimum viable task and data-service sizes | Single AZ where explicitly acceptable; retained data/backup policy required | Requires region, monthly runtime hours and session/request volume |
| Staging | Continuous integration/demo environment with repeatable test data | API tasks across AZs; data tier chosen based on recovery target | Requires test duration, DB/cache/broker sizing and retention |
| Production-like HA | Sustained workload and defined peak concurrency | Multi-AZ API/data services; explicit backup and failover targets | Requires measured Phase 7 traffic, service SLO/RTO/RPO, and region |

The Phase 7 k6 run covers five virtual users browsing the catalog for 30 seconds on a local host. It is not a cloud sizing workload and cannot support a production capacity estimate.

## Estimate inputs checklist

For each scenario, record provider/region/currency/date checked, runtime hours/month, vCPU and memory per Fargate task, desired/min/max task count, image storage and transfer, ALB hours/LCUs, CloudFront requests and data transfer, S3 storage/requests/lifecycle, RDS class/engine/storage/IOPS/Multi-AZ/PITR retention, cache node/serverless units and backups, broker type/instance/deployment mode/storage, NAT count/bytes, VPC endpoint count, log/metric/trace ingestion and retention, snapshot/cross-region copy, public egress, support/tax exclusions, and any discount assumptions.

Do not copy a free-tier or promotional-credit rate into steady-state cost. Count idle hours, backup storage, retained versions, cross-AZ bytes, and inter-region egress. Compare the same HA, retention, traffic and support level across scenarios.

## Known cost drivers and cost controls

- Fargate charges vary with vCPU, memory, OS, CPU architecture and allocated ephemeral storage over task runtime; size from measured load and use bounded autoscaling. Official [Fargate pricing](https://aws.amazon.com/fargate/pricing/) describes the metered dimensions.
- RDS class, storage, I/O, Multi-AZ, backup retention and snapshots are separate cost dimensions. Automated backups/PITR are required controls, not free capacity; review [RDS backup retention](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.BackupRetention.html).
- Amazon MQ costs depend on broker engine, instance, deployment mode and storage. RabbitMQ 4.3 availability is instance- and region-dependent; query the [Amazon MQ regional engine API](https://docs.aws.amazon.com/amazon-mq/latest/api-reference/broker-engine-types.html) for the approved region before sizing.
- NAT gateways have hourly and data-processing charges. Compare one-per-AZ versus single-NAT resilience and egress charges with interface/gateway endpoint costs in the selected region.
- ElastiCache mode/engine, node/compute, replicas, backup and transfer choices affect cost; evaluate Valkey/Redis OSS compatibility and current regional pricing.
- Observability ingestion and retention (CloudWatch logs/metrics, traces, dashboard queries), CloudFront requests/transfer, KMS key usage, Secrets Manager secret count/API calls, S3 storage and cross-region replication can be material.

## Estimate workflow

1. Select the region and service versions using current AWS availability data.
2. Generate one AWS Pricing Calculator estimate per scenario with inputs above. Save the calculator share link/export and line-item assumptions with this document.
3. Review infrastructure and network costs separately from compute; model RDS, cache, broker, NAT/endpoints, observability, backup and egress explicitly.
4. Set budget alerts and cost allocation tags before any deployment. An alert is not a spending cap.
5. After an approved deployment, compare Cost and Usage Report or Cost Explorer actuals with the estimate; state measured billing only from billing evidence.

### Official references checked 2026-11-17

- [AWS Pricing Calculator](https://calculator.aws/)
- [AWS Fargate pricing](https://aws.amazon.com/fargate/pricing/)
- [Amazon MQ documentation and version availability](https://docs.aws.amazon.com/amazon-mq/latest/developer-guide/rabbitmq-version-management.html)
- [RDS automated backup retention](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.BackupRetention.html)
- [AWS Pricing](https://aws.amazon.com/pricing/)

This is an input model, not a completed quote or actual cloud bill. Pricing can change; regenerate before deployment approval.
