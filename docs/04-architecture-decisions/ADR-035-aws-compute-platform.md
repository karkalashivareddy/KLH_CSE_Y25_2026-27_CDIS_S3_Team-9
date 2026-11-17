# ADR-035: AWS compute platform

- **Status:** Accepted target, not deployed
- **Date:** 2026-11-17
- **Context:** The project deployable unit is a Java/Spring modular monolith with static React assets; no measured requirement justifies Kubernetes operations.
- **Options:** ECS Fargate + ALB; EKS; EC2-managed containers; static assets in S3/CloudFront with API on ECS.
- **Decision:** Run the API and built React assets as separate ECS Fargate services behind one ALB; the ALB routes `/api/*` to the API and the rest to unprivileged Nginx. This keeps same-origin browser sessions/CSRF without adding an untested CloudFront policy.
- **Consequences:** Fargate reduces host operations but costs vary by requested resources/runtime and does not guarantee lower cost than EKS at all workloads. CloudFront/S3 can be added after measuring transfer/cache needs. ECS deployment circuit breaker/rollback and immutable image tags are required.
- **Reconsider when:** measured workload, team capability, deployment policy, or an actual Kubernetes-specific need justifies EKS or another target.
- **Validation:** official ECS deployment circuit breaker documentation checked 2026-11-17. No cloud plan or deployment has run. See [AWS target architecture](../03-architecture/AWS_TARGET_ARCHITECTURE.md).
