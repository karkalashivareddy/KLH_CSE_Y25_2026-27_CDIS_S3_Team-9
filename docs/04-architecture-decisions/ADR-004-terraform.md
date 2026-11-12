# ADR-004: Terraform for infrastructure as code

- **Status:** Accepted as planned tool; no IaC implemented
- **Date:** 2026-11-12
- **Context/problem:** repeatable cloud environments and reviewable changes are needed; provisioning requires state, identity, cost and safety controls.
- **Options:** Terraform; OpenTofu; provider-native templates; manual console.
- **Decision:** Terraform is the stated project baseline, selected for ecosystem and multi-provider comparison. Pin CLI/providers, use remote encrypted state with locking and least privilege once approved.
- **Justification/advantages:** declarative plans, reusable modules and broad provider availability.
- **Trade-offs/consequences:** provider-specific resource differences remain; state is sensitive and must be protected; HCL abstractions can hide complexity; no resources should be provisioned in Phase 1.
- **Reconsider when:** licensing/governance, provider support, or team constraints favor OpenTofu/provider-native tools.
- **Validation/dependencies:** fmt/validate/plan policy in a sandbox after tool installation and explicit cloud approval. Terraform absent; no misleading stub config created.
