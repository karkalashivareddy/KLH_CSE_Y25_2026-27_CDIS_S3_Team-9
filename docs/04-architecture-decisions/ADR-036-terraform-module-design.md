# ADR-036: Terraform module and state design

- **Status:** Accepted; networking module and dev environment implemented, validation evidence pending hosted CI
- **Date:** 2026-11-17
- **Context:** Network, compute, database, cache, broker and identity resources need reusable boundaries and isolated environment state. Terraform state and plans can contain sensitive values.
- **Options:** one monolithic root; reusable modules with environment roots; separate repositories per component.
- **Decision:** use a reusable networking module and a `dev` environment root for the current baseline; keep application/data resources in the root until a second environment justifies extraction. Pin Terraform/providers and commit the cross-platform provider lock file. Use separately bootstrapped, versioned, encrypted S3 state with native S3 lock file; do not commit local state or plans.
- **Consequences:** Module interfaces need typed/validated inputs and stable outputs. Independent roots add state wiring and bootstrap operations that must be documented. No module is permission to provision resources; only approved plan/apply may create cloud resources.
- **Validation:** AWS recommends S3 native state locking for supported Terraform versions: [AWS Terraform backend guidance](https://docs.aws.amazon.com/prescriptive-guidance/latest/terraform-aws-provider-best-practices/backend.html). No backend has been bootstrapped or cloud plan run.
