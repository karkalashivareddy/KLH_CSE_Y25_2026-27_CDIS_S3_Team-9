# Multi-cloud portability

**Status:** capability comparison and portability boundaries documented; AWS is the only implemented cloud target. Azure and Google Cloud mappings are architectural options, not tested deployments.

## Portability boundary

The portable core is the existing Java/Spring modular monolith, React frontend, PostgreSQL schema/migrations, HTTP API, versioned event envelope, outbox/inbox semantics, and container images. Keep provider selection at infrastructure and adapter boundaries. PostgreSQL remains the durable source of truth. Redis remains an ephemeral shared-session store. RabbitMQ remains the first broker contract. Do not claim a service is portable until an adapter contract test exercises the replacement.

| Capability | AWS primary | Azure candidate | Google Cloud candidate | Portability caveat |
|---|---|---|---|---|
| Container runtime | ECS Fargate | Azure Container Apps; AKS only if Kubernetes needs justify it | Cloud Run; GKE if cluster control is required | Autoscaling, task identity, networking and deployment rollout differ. ECS Fargate is implemented in Terraform; other targets are not. |
| Registry | ECR | Azure Container Registry | Artifact Registry | Image format is portable; identity, retention, scanning and replication policies differ. |
| PostgreSQL | RDS for PostgreSQL | Azure Database for PostgreSQL Flexible Server | Cloud SQL for PostgreSQL | PostgreSQL SQL is portable, but extensions, flags, maintenance, HA topology, PITR windows and failover behavior require testing. |
| Shared sessions | ElastiCache Redis OSS | Azure Managed Redis (confirm SKU/API availability before deployment) | Memorystore for Redis | Redis protocol compatibility does not guarantee identical engine versions, persistence, failover, TLS/auth or eviction behavior. Sessions are disposable; customers re-authenticate after loss. |
| Messaging | Amazon MQ for RabbitMQ (Terraform uses CloudFormation for secret-safe broker creation) | Azure Service Bus for a native managed queue, or a separately supported RabbitMQ deployment | Pub/Sub | These are not wire-compatible replacements. Service Bus lock/settlement/DLQ and Pub/Sub ack/retry/order/DLQ semantics differ from RabbitMQ confirms, routing keys and TTL retry queues. Provider-specific adapters need contract tests. |
| Object storage | S3 | Blob Storage | Cloud Storage | API, consistency details, lifecycle, versioning, retention, IAM and signed URL behavior vary. No application object storage adapter is currently required. |
| Secrets and keys | Secrets Manager, KMS | Key Vault | Secret Manager, Cloud KMS | Rotation, workload identity, envelope encryption and secret version references differ. Never copy provider secret APIs into domain logic. |
| Network | VPC, security groups, endpoints | VNet, NSGs, Private Link | VPC, firewall rules, Private Service Connect | Private service access and egress costs require provider-specific designs. The AWS Terraform network is not cross-cloud code. |
| Telemetry | CloudWatch plus OTLP | Azure Monitor plus OTLP | Cloud Monitoring plus OTLP | OpenTelemetry OTLP is a portability path; provider-specific retention, query language, sampling and cost remain different. |
| Workload identity | IAM task roles | Managed identities | Service accounts / Workload Identity | Do not attempt a shared IAM abstraction. Map least privilege to each provider identity model. |

## Messaging differences to resolve before an adapter

- RabbitMQ uses exchanges, bindings/routing keys, publisher confirms, consumer acknowledgements and broker-configured dead lettering. Preserve the application guarantee as at-least-once plus idempotency.
- Azure Service Bus uses queue/topic entities, peek-lock settlement, delivery counts, and associated DLQs; a message that repeatedly cannot be settled may be dead-lettered after the configured delivery limit. It needs an adapter mapping event metadata and replay audit to this model. [Service Bus DLQ behavior](https://learn.microsoft.com/en-us/azure/service-bus-messaging/service-bus-dead-letter-queues)
- Google Pub/Sub defaults to at-least-once delivery and no ordering guarantee; ordering keys and a configured dead-letter topic change operational behavior. Retry policies and delivery attempt counts are subscription settings. [Pub/Sub subscription behavior](https://docs.cloud.google.com/pubsub/docs/subscription-overview), [retry policy](https://docs.cloud.google.com/pubsub/docs/subscription-retry-policy)
- Do not promise equivalent ordering, replay, retention, retry delay or DLQ tooling across providers. The event ID, schema version, correlation ID and consumer inbox remain application-owned invariants.

## Database and recovery portability

Managed PostgreSQL products provide different HA/backup configurations, even when all expose PostgreSQL. Azure Flexible Server documents zone-redundant HA and PITR within backup retention; validate region/SKU settings in an actual plan before relying on them. [Azure PostgreSQL reliability](https://learn.microsoft.com/azure/reliability/reliability-postgresql-flexible-server). Cloud SQL backup/PITR and HA options are configured separately and must be validated per instance and region. [Cloud SQL PostgreSQL backups](https://cloud.google.com/sql/docs/postgres/backup-recovery/backups), [HA configuration](https://cloud.google.com/sql/docs/postgres/high-availability).

The AWS dev Terraform defaults are intentionally cost-oriented and not a multi-cloud or production HA proof. AWS database defaults include a single-AZ development configuration; change it and size/retention settings only from approved RTO/RPO and cost assumptions.

## Migration sequence

1. Keep the existing provider-neutral API, event envelope, idempotency, and database migration contracts.
2. Define one narrow port for an actual provider-specific need; do not generalize without a second implementation.
3. Add provider adapter contract tests for retries, duplicate delivery, acknowledgment timing, DLQ and replay audit.
4. Run database migration and restore tests against the destination managed PostgreSQL version and extensions.
5. Compare IAM, private networking, secret rotation, telemetry retention and estimated costs before a deployment decision.
6. Record region, service/SKU, tested semantics and known differences in a provider ADR. No Azure/GCP deployment or adapter has been implemented or tested in this project.

## Official references checked 2026-11-18

- AWS: [ECS Fargate](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/AWS_Fargate.html), [RDS PostgreSQL](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html), [Amazon MQ RabbitMQ](https://docs.aws.amazon.com/amazon-mq/latest/developer-guide/rabbitmq.html), [S3](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Welcome.html).
- Azure: [Container Apps](https://learn.microsoft.com/azure/container-apps/overview), [PostgreSQL Flexible Server](https://learn.microsoft.com/azure/postgresql/flexible-server/overview), [Service Bus](https://learn.microsoft.com/azure/service-bus-messaging/service-bus-messaging-overview), [Blob Storage](https://learn.microsoft.com/azure/storage/blobs/storage-blobs-overview).
- Google Cloud: [Cloud Run](https://cloud.google.com/run/docs/overview/what-is-cloud-run), [Cloud SQL PostgreSQL](https://cloud.google.com/sql/docs/postgres), [Pub/Sub](https://cloud.google.com/pubsub/docs/overview), [Cloud Storage](https://cloud.google.com/storage/docs/introduction).

Product availability, service names and supported features can change. Recheck official regional availability, versions and API support before selecting a target. This comparison is not evidence of a migration or failover test.
