# Phase 8: Terraform and AWS infrastructure report

**Status:** Terraform source implemented and locally validated; hosted CI, IaC scans, container scans, dependency scan and secret scan passed on the exact `main` commit. **No cloud resources were provisioned.**

**Date:** 2026-11-17
**Branch:** `main` (repository-owner directed; no PRs)
**Base commit:** `27d8be7` (Phase 7 report finalized)
**Implementation commit:** `1fe7b1308958b974758b069abbced544e48ac5c3`
**CI run:** [44108903172](https://github.com/karkalashivareddy/KLH_CSE_Y25_2026-27_CDIS_S3_Team-9/actions/runs/44108903172) (success, exact SHA)

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
| TFLint / Checkov | PASS | Hosted run 44108903172; required checks completed successfully |
| API integration tests and package | PASS | Hosted `mvn verify -DrunIntegrationTests=true` succeeded; Docker image build and Trivy scan also succeeded |
| Frontend checks and image scan | PASS | Hosted web job succeeded, including tests/build and Trivy scan |
| Gitleaks / OSV | PASS | Hosted secret and Maven vulnerability scan jobs succeeded |
| AWS credentialed plan | NOT RUN | No AWS credentials, approved account, region, or cost approval; no fake plan claimed |
| Terraform apply / AWS resources | NOT AUTHORIZED / NOT RUN | No cloud provisioning performed |
| Cost calculator estimate | NOT RUN | Workload and region assumptions were not provided; no invented figures included |

## Known limitations

- CI result is success on `1fe7b1308958b974758b069abbced544e48ac5c3`.
- Checkov exceptions are inline with explanations for dev-only cost choices and provider-resource correlation gaps; WAF, TLS, IAM DB auth, RDS monitoring, KMS logs, versioning and VPC default-SG controls are enabled.
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
