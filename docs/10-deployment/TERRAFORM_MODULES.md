# Terraform modules and validation

**Status (2026-11-17):** a dev environment and networking module are implemented and locally formatted/validated. No state backend has been bootstrapped and no AWS resources have been planned or provisioned.

## Repository layout

```text
infrastructure/terraform/
  .tflint.hcl
  modules/networking/       # VPC, AZ subnets, routes, NAT, flow logs
  environments/dev/         # endpoints, IAM/SGs, KMS, RDS, cache, MQ, ECR, ALB, ECS
    .terraform.lock.hcl     # pinned AWS/random checksums for Linux and Windows
    backend.hcl.example     # S3 backend values; account-specific bucket is external
    terraform.tfvars.example
```

Application/data resources currently live in the dev root. Extract them into separate modules when a second environment exists and stable input/output contracts can be verified. Staging resources and state are not implemented.

## Implemented controls

- Terraform CLI 1.17.1; AWS provider 6.71.0; random provider 3.9.0. Exact provider selections and checksums are committed in the dev lock file for `linux_amd64` and `windows_amd64`.
- Two-AZ VPC with public ALB/NAT subnets, private application subnets, isolated RDS/cache subnets, flow logs with bounded retention, and one NAT gateway as a dev cost/availability trade-off.
- Interface endpoints for ECR, Secrets Manager and CloudWatch Logs, plus an S3 gateway endpoint for ECR layers. Application egress is scoped to dependency security groups and those endpoints.
- ECR immutable commit-SHA tags and lifecycle rules; HTTPS ALB with HTTP redirect, API path routing, actuator-path 404, ECS Fargate services, health checks and deployment rollback circuit breaker.
- RDS PostgreSQL, TLS-enabled ElastiCache Redis OSS sessions, and private Amazon MQ RabbitMQ; stateful resources have deletion safeguards and backups where supported.
- Separate API and web execution roles. Only the API execution role can retrieve application secrets; task roles have no extra permissions.
- Passwords use Terraform ephemeral values and provider write-only arguments. Amazon MQ uses a CloudFormation Secrets Manager dynamic reference because the AWS Terraform MQ resource stores broker usernames/passwords in plaintext state.
- S3 remote-state example uses encryption and native lock files. The state bucket, KMS key, IAM policy, and access logging must be bootstrapped separately before a credentialed plan.

## Validation

Local commands executed with Terraform 1.17.1:

```powershell
terraform fmt -check -recursive infrastructure/terraform
terraform -chdir=infrastructure/terraform/environments/dev init -backend=false -input=false -upgrade
terraform -chdir=infrastructure/terraform/environments/dev validate
```

All three completed successfully after pinning providers. TFLint, Checkov, and hosted container builds are pending the Phase 8 CI run. No credentialed plan, apply, destroy, AWS resource, remote state, or bill was created.

## Backend and deployment constraints

1. Separately create a versioned, KMS-encrypted state bucket with public access blocked, native S3 locking enabled, and scoped IAM. Do not put its values or state in Git.
2. Review state access and CloudFormation dynamic-reference support for the selected Amazon MQ region/engine before any deployment.
3. Replace example certificate/image tags and confirm engine/instance availability in the selected region.
4. Review a credentialed, no-apply plan under a protected GitHub OIDC environment. The repository currently validates source without AWS credentials; it has no AWS deployment workflow or OIDC role.
5. No `apply` or `destroy` is authorized by this project task.
