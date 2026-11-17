# AWS deployment guide

**Status:** Terraform is implemented and locally validated. Hosted scans remain the Phase 8 gate. No AWS plan, deployment, or provisioning has occurred. This guide describes the controlled future workflow; do not run `terraform apply`, DNS changes, or create paid resources as part of validation.

## Preconditions

1. Record the approved AWS account and region, environment owner, budget, recovery targets, DNS/domain owner, retention and deletion policy.
2. Produce a current AWS Pricing Calculator estimate for the exact region, service tiers, storage, runtime, traffic, NAT/endpoints, logs, backups and support; obtain budget approval and configure budget alerts.
3. Review the security model, threat model, data classification, break-glass access and operations runbooks.
4. Confirm region/service/engine availability, especially Amazon MQ RabbitMQ version and broker class, RDS PostgreSQL version/class, ElastiCache engine and ECS Fargate capacity.
5. Bootstrap an encrypted/versioned Terraform state bucket and state locking separately. No backend state or credentials belong in Git.
6. Before deployment automation, configure GitHub OIDC trust restricted to the repository, `main`, and a protected deployment environment. This OIDC role and protected deployment workflow are not implemented yet.

## Build and release flow

1. CI runs Maven integration tests, frontend checks, secret/dependency scans, container scans, IaC checks and Terraform validation without AWS credentials.
2. Build API and web artifacts; scan and publish immutable images/artifacts. Use a full commit SHA as the release identity, never `latest`.
3. Generate a Terraform plan from reviewed source and short-lived OIDC credentials after an approved deployment workflow exists. Store plans as restricted, short-lived artifacts because they can contain sensitive infrastructure data.
4. Review the plan for public exposure, IAM, KMS, backup retention, deletion protection, cost-driving changes and replacement of stateful resources.
5. Require protected environment approval before deployment. Apply only the approved plan; a changed plan requires a new review.
6. Run a controlled Flyway migration task, wait for health gates, then deploy immutable API and frontend artifacts with ECS rollback configured.
7. Verify ALB and ECS web/API health, API readiness, private data connectivity, session/CSRF behavior, broker confirms and consumer health, backups, alarms, logs and traces.
8. Record deployed commit, image digests, Terraform state lineage/serial, operator, approval, plan/apply run links, health checks and cost tags.

## Network and runtime controls

- Public subnets contain the ALB and NAT gateways. API and web ECS tasks have no public IP.
- Private application subnets contain ECS Fargate tasks; ingress is restricted to the ALB security group and egress to required data services/endpoints.
- Isolated data subnets contain RDS, cache and broker. They have no internet-gateway route and only accept the application task security group on service ports.
- Current Terraform serves the frontend from an unprivileged Nginx ECS task behind the ALB. CloudFront and a private S3 static origin are future options, not implemented resources.
- Runtime task roles are distinct from execution roles. Execution permissions are limited to image pull, logging, and required secret retrieval. Application roles have only required service actions.
- Secrets Manager references are injected at runtime. Rotate through an operator-reviewed procedure; secret values must not appear in Terraform outputs, Git environment files, logs, artifacts or Docker build arguments.

## Rollback and migration

ECS rollback restores the prior task definition/image only when the older application remains compatible with the current schema. Use expand/contract database changes: additive migration, deploy compatible code, backfill with a bounded job if needed, then remove old columns only in a later release. Do not automatically reverse destructive migrations. A database restore is a disaster-recovery operation, not an ordinary deployment rollback.

## Teardown

Only an explicitly approved non-production environment may be destroyed. Export required evidence, confirm backups and data retention, enumerate Terraform state resources, and get the environment owner's approval. Protect production databases/buckets and Terraform state from routine teardown. No teardown has been executed.

## Status language

"Designed" means documented. "Terraform implemented" means source exists. "Validated" means formatting, initialization without a backend and validation passed; hosted scanners are tracked separately. "Planned" means a reviewed plan exists. "Provisioned" means AWS confirms resources exist. "Deployed" means an application release passed runtime checks. Current status: Terraform source is locally validated; no plan or resources exist.
