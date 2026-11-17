# Phase 8: Terraform and AWS infrastructure report

**Status:** Terraform source implemented; local formatting, backend-free initialization, and validation passed. Hosted CI, IaC scans, and container scans are pending. **No cloud resources were provisioned.**

**Date:** 2026-11-17
**Branch:** `main` (repository-owner directed; no PRs)
**Base commit:** `27d8be7` (Phase 7 report finalized)
**Implementation commit:** pending
**CI run:** pending

## Implemented scope

- A reusable networking module and a development root environment under `infrastructure/terraform/` define a two-AZ VPC, public ALB/NAT, private application subnets, isolated data subnets, flow logs, selected VPC endpoints, ECR, ECS Fargate API and web tasks, ALB routing, RDS PostgreSQL, Redis OSS sessions, Amazon MQ RabbitMQ, KMS, and Secrets Manager.
- Data services are private. ECS task and execution roles are separate; ECR tags are immutable; the Nginx web container runs unprivileged. Stateful resources have backup and deletion safeguards.
- Terraform and AWS provider versions are pinned; `.terraform.lock.hcl` contains checksums for Windows and Linux AMD64.
- RDS, Redis, and Secrets Manager values use Terraform write-only arguments with ephemeral generated passwords. Amazon MQ is defined through a CloudFormation stack with a Secrets Manager dynamic reference because the direct Terraform broker resource stores credentials in state. This integration has not been exercised against AWS.
- The web image is served by unprivileged Nginx on port 8080 behind the ALB. CloudFront/S3 static hosting remains a future option. No AWS credentials, account, region, or approved workload assumptions were available for a credentialed plan or cost estimate.
- The regular CI workflow is configured to run on `main`; it includes Terraform validation/scanning and backend/frontend/container checks. An AWS OIDC deployment role/workflow is not implemented.

## Verification evidence

| Check | Status | Evidence |
|---|---|---|
| `terraform fmt -check -recursive infrastructure/terraform` | PASS | Local Terraform 1.17.1 |
| `terraform -chdir=infrastructure/terraform/environments/dev init -backend=false -input=false -upgrade` | PASS | Providers resolved; no remote state or AWS account used |
| `terraform -chdir=infrastructure/terraform/environments/dev validate` | PASS | Local validation after the MQ and write-only secret changes |
| Docker Compose configuration | PASS | `docker compose config --quiet`; Docker emitted a local config-file warning |
| Frontend lint/typecheck/tests/build | PASS | 21 tests across 6 files; lint, typecheck and production build passed |
| TFLint / Checkov | NOT RUN | Hosted CI pending |
| API integration tests / Gitleaks / OSV / Trivy | NOT RUN FOR THIS CHANGESET | Hosted CI pending; Docker engine unavailable locally |
| AWS credentialed plan | NOT RUN | No AWS credentials, approved account, region, or cost approval; no fake plan claimed |
| Terraform apply / AWS resources | NOT AUTHORIZED / NOT RUN | No cloud provisioning performed |
| Cost calculator estimate | NOT RUN | Workload and region assumptions were not provided; no invented figures included |

## Known limitations

- Hosted CI must pass on the exact pushed `main` SHA before Phase 8 can be considered integrated.
- TFLint, Checkov, and image scan results are pending hosted execution.
- Service/API compatibility, region availability, CloudFormation secret-reference behavior, and a credentialed plan have not been verified against an AWS account.
- GitHub OIDC deployment setup is documented as a future controlled workflow, not implemented.
- Pricing estimate is deferred until a region and reproducible workload assumptions are selected.
- No real AWS resources or cloud recovery drills exist.

## Exact local commands

```text
terraform fmt -check -recursive infrastructure/terraform
terraform -chdir=infrastructure/terraform/environments/dev init -backend=false -input=false -upgrade
terraform -chdir=infrastructure/terraform/environments/dev validate
docker compose config --quiet
```

No deploy, plan, apply, destroy, account creation, public DNS change, or paid cloud operation occurred.
