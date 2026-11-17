# ADR-037: private-by-default AWS network

- **Status:** Accepted target, not deployed
- **Date:** 2026-11-17
- **Context:** The application requires browser ingress, API compute, PostgreSQL, session storage and RabbitMQ. Data services carry sensitive state and should not have public endpoints.
- **Options:** public tasks/data services; public ALB with private tasks and private data tiers; private-only service with VPN.
- **Decision:** use public subnets only for the ALB and NAT gateway; API and Nginx web ECS tasks in private subnets; RDS/cache/broker in isolated private subnets. Security groups reference the ALB, application, data-service and endpoint groups on required ports. Application egress uses private AWS endpoints and the regional S3 prefix list; the dev network has one NAT gateway.
- **Consequences:** NAT and endpoint choices affect fixed and transfer costs. Private egress must still support image retrieval, secret/log/telemetry delivery and required external APIs. Database/cache/broker are never public. VPC Flow Logs and bounded retention are included in the target.
- **Validation:** architecture review only; no Terraform validation or AWS provisioning is claimed. See [AWS target architecture](../03-architecture/AWS_TARGET_ARCHITECTURE.md) and [cost estimate model](../08-finops/AWS_COST_ESTIMATION.md).
