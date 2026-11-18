# Multi-cloud cost comparison

**Pricing sources checked:** 2026-11-18. **Estimate status:** NOT ESTIMATED for AWS, Azure, and Google Cloud. There is no approved deployment region, workload profile, service sizing, retention policy, or billing currency for this project. Accordingly, this document supplies a reproducible comparison method and records the missing inputs; it does not publish speculative monthly totals, a provider ranking, or savings claims. No cloud resources or bills were used.

## Scope and method

Compare equivalent capability and service objectives, not product names. For each scenario, use the providers' official calculators with identical assumptions for runtime, API capacity, database capacity and availability, message volume and retention, cache capacity, traffic, logs, backups, and recovery. Save each calculator's dated share link or export with the completed estimate. Exclude free credits and free tiers from the steady-state comparison. Record discount commitments separately from on-demand estimates.

Monthly total should include, at minimum:

```text
application compute + registry/image storage + load balancing/ingress
+ PostgreSQL compute + storage + IOPS + HA + backup/PITR
+ cache capacity + replicas/HA + persistence/backup
+ messaging capacity/operations + retained messages + HA
+ NAT + endpoints + cross-zone/region transfer + internet egress
+ logs + metrics + traces + retention + dashboards
+ object storage + snapshots + cross-region copies
+ secrets/key usage + support plan + applicable taxes
```

Use each calculator's native billing units and SKU selections. Do not convert unlike durability, SLA, broker semantics, or support tiers into a false equivalent. Keep USD and local-currency estimates distinct and record the calculator exchange-rate date if currencies differ.

## Capability mapping and comparability limits

| Capability | AWS comparison candidate | Azure comparison candidate | Google Cloud comparison candidate | Cost-model note |
|---|---|---|---|---|
| Container API | ECS on Fargate | Azure Container Apps (or AKS only if Kubernetes is required) | Cloud Run (or GKE only if Kubernetes is required) | Match allocated CPU/memory, minimum warm capacity, scale limits, runtime, and network attachment. Scale-to-zero changes latency and availability assumptions. |
| PostgreSQL | RDS for PostgreSQL | Azure Database for PostgreSQL Flexible Server | Cloud SQL for PostgreSQL | Match vCPU/memory, storage type/size, IOPS, zone/region HA, backup/PITR retention, and read replicas. Verify engine/version and region availability. |
| Redis-compatible sessions | ElastiCache for Valkey/Redis OSS after compatibility check | Azure Managed Redis | Memorystore for Valkey/Redis after compatibility check | Match memory, throughput, HA/replicas, persistence, and network. Product compatibility and supported commands/features differ. Azure Cache for Redis is on a retirement path; use Azure Managed Redis for a new comparison. |
| Messaging | Amazon MQ RabbitMQ for closest broker fit; SQS only with an adapter and semantic review | Service Bus requires an adapter; it is not RabbitMQ | Pub/Sub requires an adapter; it is not RabbitMQ | Do not price these as equivalent by message count alone. Compare delivery/ordering semantics, acknowledgement, retry, DLQ, retention, throughput, HA, and application changes. |
| Container registry | ECR | Azure Container Registry | Artifact Registry | Match stored image GB-month, pull count, replication, and egress. |
| Object/backup storage | S3 | Blob Storage | Cloud Storage | Match storage class, versioning, retention, requests, encryption, replication, and retrieval. |
| Load balancing / ingress | Application Load Balancer / optional CloudFront | Azure Load Balancer / Application Gateway / Front Door as required | Cloud Load Balancing / optional Cloud CDN | Match public ingress architecture, request/LCU-equivalent usage, TLS, WAF, and outbound data. |
| Network egress | NAT Gateway, VPC endpoints, data transfer | NAT Gateway, Private Link, bandwidth | Cloud NAT, Private Service Connect, network egress | Include gateway hours and processed bytes, endpoint charges, cross-zone/region traffic, and public egress. |
| Logs, metrics, traces | CloudWatch plus chosen OTEL backend | Azure Monitor / Log Analytics plus OTEL | Cloud Logging / Monitoring plus OTEL | Match ingestion GB/day, metric cardinality, trace sampling, query volume, and retention. |
| Secrets and keys | Secrets Manager and KMS | Key Vault / Managed HSM only if required | Secret Manager and Cloud KMS | Match number of secrets/keys, API operations, key type, and rotation frequency. |

These are candidates, not claims of feature or price equivalence. Provider products and supported regions change; verify them before making the estimate. In particular, keeping RabbitMQ's existing semantics may favor a compatible broker instead of a cloud-native queue, while switching to a cloud-native service requires adapter and contract testing.

## Scenario worksheet

Complete one worksheet per scenario and provider. The current project has not approved these inputs, so the value column remains open rather than defaulting to guessed values.

| Input | Academic/demo | Staging | Production-like HA | Evidence / source |
|---|---|---|---|---|
| Provider, region, currency, quote date | **OPEN** | **OPEN** | **OPEN** | Region and currency selection in official calculator |
| API runtime hours/month; min, desired, max replicas | **OPEN** | **OPEN** | **OPEN** | Deployment schedule and measured workload |
| API CPU/memory, architecture, warm/min instances | **OPEN** | **OPEN** | **OPEN** | Load test and selected provider SKU |
| Requests/month, peak concurrency, request duration | **OPEN** | **OPEN** | **OPEN** | Reproducible Phase 7/10 load test; current local smoke test is insufficient |
| Container images stored/pulled, replication, egress | **OPEN** | **OPEN** | **OPEN** | Registry policy and release cadence |
| PostgreSQL engine/version, CPU/memory, HA topology | **OPEN** | **OPEN** | **OPEN** | Compatibility check, RTO/RPO target, measured DB load |
| PostgreSQL storage, IOPS/throughput, growth/month | **OPEN** | **OPEN** | **OPEN** | Seed data and measured growth |
| PostgreSQL backup/PITR retention and cross-region copies | **OPEN** | **OPEN** | **OPEN** | Approved backup policy and recovery design |
| Cache size, throughput, HA/replicas, persistence | **OPEN** | **OPEN** | **OPEN** | Session count/size and session-loss policy |
| Messaging product, delivery rate, message size, subscriptions/consumers | **OPEN** | **OPEN** | **OPEN** | Event measurements and adapter decision |
| Message retention, retries, DLQ retention, broker HA | **OPEN** | **OPEN** | **OPEN** | Event recovery and operational policy |
| Inbound/outbound GB, cross-zone and cross-region GB | **OPEN** | **OPEN** | **OPEN** | Representative test/traffic assumptions |
| NAT gateways, endpoints, public IPs, WAF rules/requests | **OPEN** | **OPEN** | **OPEN** | Approved network design |
| Logs, metrics, traces GB/day, query volume, retention | **OPEN** | **OPEN** | **OPEN** | Observability configuration and measured telemetry |
| Object, snapshot and image storage; retrieval and lifecycle | **OPEN** | **OPEN** | **OPEN** | Backup/retention policy |
| Support tier, discounts/commitments, taxes | **OPEN** | **OPEN** | **OPEN** | Procurement/account assumptions; keep list price separately |
| Calculator share URL and exported line items | **NOT CREATED** | **NOT CREATED** | **NOT CREATED** | Attach after all input fields above are approved |
| Monthly estimate (provider currency) | **NOT ESTIMATED** | **NOT ESTIMATED** | **NOT ESTIMATED** | No output until quote is reproducible |

## Reproduction procedure

1. Approve the same scenario assumptions for all three providers, including region, currency, availability, retention, and support exclusions.
2. Verify each selected service is currently available in the chosen region and supports the required engine/version and network design.
3. Enter equivalent compute, storage, request, transfer, retention, and HA inputs in the official calculators below. Add optional services such as NAT, observability, backup, WAF, and replication as separate line items.
4. Save the estimate share URL and CSV/PDF export. Record SKU, unit, quantity, hours, rate, discounts, currency, taxes/support exclusions, and retrieval date for every line.
5. Review hidden cost drivers: minimum instance hours, HA replicas, cross-zone traffic, NAT processing, log/trace ingestion and queries, backup/PITR storage, image pulls, and internet egress.
6. Have an independent reviewer verify scenario parity and calculator arithmetic. Report ranges only when each range endpoint has an explicit documented assumption.
7. Once an approved environment exists, compare estimates with provider billing exports and measured utilization. Label them separately as estimate and actual; do not infer savings without a baseline and usage evidence.

## Official calculators and product pricing references

The calculators are the authoritative starting points for region- and configuration-specific quotes. Public service pages can show example rates or explain billing dimensions, but an example from another region is not a project estimate.

| Provider | Calculator | Useful official pricing references |
|---|---|---|
| AWS | [AWS Pricing Calculator](https://calculator.aws/) | [Fargate](https://aws.amazon.com/fargate/pricing/), [RDS for PostgreSQL](https://aws.amazon.com/rds/postgresql/pricing/), [Amazon MQ](https://aws.amazon.com/amazon-mq/pricing/), [ElastiCache](https://aws.amazon.com/elasticache/pricing/), [NAT Gateway](https://aws.amazon.com/vpc/pricing/), [CloudWatch](https://aws.amazon.com/cloudwatch/pricing/), [S3](https://aws.amazon.com/s3/pricing/) |
| Azure | [Azure Pricing Calculator](https://azure.microsoft.com/pricing/calculator/) | [Container Apps](https://azure.microsoft.com/pricing/details/container-apps/), [PostgreSQL Flexible Server](https://azure.microsoft.com/pricing/details/postgresql/flexible-server/), [Service Bus](https://azure.microsoft.com/pricing/details/service-bus/), [Azure Managed Redis overview and tiers](https://learn.microsoft.com/azure/redis/overview), [Azure Cache for Redis retirement FAQ](https://learn.microsoft.com/azure/azure-cache-for-redis/retirement-faq), [Azure Monitor](https://azure.microsoft.com/pricing/details/monitor/) |
| Google Cloud | [Google Cloud Pricing Calculator](https://cloud.google.com/products/calculator) | [Cloud Run](https://cloud.google.com/run/pricing), [Cloud SQL](https://cloud.google.com/sql/pricing), [Pub/Sub](https://cloud.google.com/pubsub/pricing), [Memorystore](https://cloud.google.com/memorystore/pricing), [Cloud NAT](https://cloud.google.com/nat/pricing), [Cloud Logging](https://cloud.google.com/logging/pricing), [Cloud Storage](https://cloud.google.com/storage/pricing) |

## Current conclusion and limitations

- AWS is the project's primary target architecture; Azure and Google Cloud are comparison targets, not implemented deployment environments.
- The following are concrete blockers to a defensible quote, rather than missing calculator work: no provider regions/currency are approved; no AWS/Azure/GCP account, service deployment, or selected SKU exists; no recurring request/message/egress/log-volume workload is measured; no cloud-sized database/cache/broker capacity or retention is measured; and no HA/RTO/RPO choice is approved. The local checkout E2E and synthetic PostgreSQL recovery fixture validate behavior, but do not provide monthly utilization or storage-growth inputs.
- Until those values are approved, the open rows in the scenario worksheet must stay `OPEN` and the monthly totals must stay `NOT ESTIMATED`. Choosing illustrative defaults can be useful for planning, but would produce an assumption-only scenario and must not be presented as an estimate for this project.
- The documented Phase 7 local catalog load test is not a representative cloud capacity or pricing baseline. Do not extrapolate it into request volume, database sizing, or monthly compute hours.
- A provider ranking, monthly cost, cost saving percentage, or break-even point is **NOT ESTIMATED** until the worksheet is approved and calculator exports are attached.
- Calculators estimate provider charges from entered configuration and usage; actual bills can differ due to actual consumption, negotiated terms, exchange rates, and taxes. Re-run estimates immediately before deployment decisions.
- Pricing and product availability were reviewed on **2026-11-18**. Revalidate all sources and regional capabilities when completing a quote.
