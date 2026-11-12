# ADR-005: AWS as proposed primary cloud

- **Status:** Accepted as project target, deployment not approved
- **Date:** 2026-11-12
- **Context/problem:** project needs a concrete primary architecture and two comparison providers; repository assignment targets AWS skills.
- **Options:** AWS-first; Azure-first; GCP-first; multi-cloud active-active.
- **Decision:** document AWS as primary target; compare Azure and GCP; no account/resource setup without approval.
- **Justification/advantages:** provides one coherent deployment target while retaining a bounded portability study.
- **Trade-offs/consequences:** vendor-specific IAM, queues, networking and monitoring; multi-cloud comparison adds research cost; AWS pricing/account/region are unknown.
- **Reconsider when:** course/team account access, region, budget, or required capability favors another provider.
- **Validation/dependencies:** architecture and cost review with current provider docs before any plan/apply. Related mapping/FinOps.
