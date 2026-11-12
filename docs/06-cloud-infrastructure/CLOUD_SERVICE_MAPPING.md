# Cloud service mapping

**Purpose/scope:** capability comparison, not a claim of equivalent behavior, current usage, or price. Pricing depends on region, workload, egress, retention, and tier; obtain current provider estimates before approval. Related: portability strategy, ADR-005/007, cost strategy.

| Capability | AWS (proposed primary) | Azure | Google Cloud | Differences and portability concern |
|---|---|---|---|---|
| Container execution | ECS Fargate | Container Apps | Cloud Run | Scaling-to-zero, concurrency, networking, job semantics, and billing differ. |
| Kubernetes | EKS | AKS | GKE | Control-plane/worker costs, IAM, networking and managed add-ons differ; don't select absent need. |
| Object storage | S3 | Blob Storage | Cloud Storage | Consistency/versioning/lifecycle/retention, ACL/IAM, event behavior and egress vary. |
| PostgreSQL | RDS PostgreSQL | Azure Database for PostgreSQL | Cloud SQL for PostgreSQL | Extensions, versions, HA/failover, backup retention and maintenance controls differ. |
| Messaging | SQS + SNS | Service Bus | Pub/Sub | Ordering, delivery, sessions, DLQ, retention, fan-out and acknowledgment semantics differ. |
| Secrets | Secrets Manager | Key Vault | Secret Manager | Rotation integration, identity bindings, quotas and versions differ. |
| Identity | IAM | Entra ID + managed identities | Cloud IAM | Principal/resource model and condition semantics are not interchangeable. |
| Monitoring | CloudWatch | Azure Monitor | Cloud Monitoring | Query languages, metric dimensions, retention and charges differ; use OTel selectively. |
| Registry | ECR | Azure Container Registry | Artifact Registry | Scanning/signing/replication policies and pricing differ. |

Migration must map data, identity, network, keys, observability and rollback. Estimate compute, storage, requests, egress, backups, logs, support, and idle time under equivalent assumptions. Use adapters only behind real app-owned contracts; no provider adapter is implemented in Phase 1. Validate with provider documentation, sandbox contracts and cost estimate before choice.
