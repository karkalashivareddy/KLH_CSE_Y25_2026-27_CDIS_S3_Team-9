# ADR-006: Prefer ECS Fargate before EKS

- **Status:** Accepted for initial AWS target
- **Date:** 2026-11-12
- **Context/problem:** container hosting is needed; managed Kubernetes adds cluster/add-on/security/upgrades and observability overhead.
- **Options:** ECS Fargate; EKS; EC2-managed containers; serverless task execution.
- **Decision:** start with ECS Fargate for the modest modular API and workers; consider EKS only if Kubernetes-specific requirements are evidenced.
- **Justification/advantages:** less cluster operations and capacity management, task-level scaling, suitable for a small team; preserves learning focus on networking, IAM, containers and recovery.
- **Trade-offs/consequences:** less Kubernetes portability/ecosystem; per-task pricing and scaling behavior need workload estimates; not automatically cheaper than EKS at all scales.
- **Reconsider when:** need for CRDs/operators, complex scheduling, workload density, platform policy, or demonstrated cross-cloud Kubernetes portability outweighs control-plane/operations costs.
- **Validation/dependencies:** compare representative workload and total ops cost; deployment remains future/unauthorized.
