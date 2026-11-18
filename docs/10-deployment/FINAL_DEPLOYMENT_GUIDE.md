# Final deployment guide

**Status:** local Compose and CI flows are implemented. Terraform describes and validates an AWS target; no AWS account-backed plan, apply or deployment has been performed.

## Local development

Use [Local development](LOCAL_DEVELOPMENT.md) for prerequisites, environment setup and commands. The local system is Docker Compose: React static assets served through unprivileged Nginx, Spring Boot API, PostgreSQL, Redis sessions and RabbitMQ. Optional observability uses the documented Compose profile. Use only synthetic/local credentials from `.env.example`; replace them for local use and never place secrets in source or browser bundles.

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Keep PostgreSQL, Redis and RabbitMQ bound to local/private interfaces. The Compose RabbitMQ management endpoint is for local development only. Do not expose development dashboards publicly.

## CI validation

The main-branch workflow runs frontend lint/typecheck/unit tests/build, backend Maven verification with Testcontainers, container builds/scans, Terraform formatting/validation/TFLint/Checkov, Gitleaks and OSV. The browser job uses a disposable Compose database and synthetic fixture, runs Playwright, executes an isolated PostgreSQL order/payment restore, and then checks the deployable web image and passive ZAP target. Each check is PASS only when the exact hosted job executed successfully; inspect [the final validation matrix](../09-testing/FINAL_VALIDATION_MATRIX.md) for the latest verified run.

GitHub Actions has read-only repository contents permission in the normal workflow. It does not deploy to AWS. No static cloud access keys are used.

## AWS target readiness

The target design is in [AWS target architecture](../03-architecture/AWS_TARGET_ARCHITECTURE.md) and [Terraform module guide](TERRAFORM_MODULES.md). Validate source without provisioning:

```text
terraform fmt -check -recursive infrastructure/terraform
terraform -chdir=infrastructure/terraform/environments/dev init -backend=false -input=false
terraform -chdir=infrastructure/terraform/environments/dev validate
tflint --chdir=infrastructure/terraform --recursive
```

CI also runs Checkov. Backend-free initialization and static validation do not prove AWS account permissions, provider availability, a costed plan or resource health. Do not run `terraform apply` or create cloud resources as part of routine verification.

Before an approved deployment, maintainers still need to select account and region, bootstrap protected remote state, configure GitHub OIDC trust and least-privilege roles, set environment secrets, approve budgets and recovery targets, and review an account-backed plan. Use immutable image digests/tags, protected deployment environments and an explicit production approval.

## Recovery and rollback

Use [restore runbook](../07-disaster-recovery/RESTORE_RUNBOOK.md) and [final recovery summary](../12-disaster-recovery/FINAL_RECOVERY_SUMMARY.md) to recover local database state. The tested CI drill is synthetic and does not qualify as production recovery. AWS rollback, RDS restoration, managed cache/broker recovery and region failover remain design-only until provisioned and exercised.

## Current deployment status

| Capability | Status |
|---|---|
| Local Docker Compose | Implemented; run using synthetic/local settings |
| Main CI | Implemented; consult run-specific job results |
| AWS Terraform | Source implemented; CI static validation passes on earlier exact commits; no account-backed plan or apply |
| AWS/Azure/GCP resources | None provisioned |
| Production traffic/data | None |
| Costed environment | Not estimated; region and workload assumptions are open |
