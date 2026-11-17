# Cloud IAM and secrets

**Status:** controls implemented in Terraform source; no AWS account, IAM role, secret or KMS key has been created.

## Human and workload identities

- Human operators use federated identity/SSO with MFA and role assumption. Avoid long-lived IAM users and access keys for routine administration.
- GitHub Actions uses OIDC to obtain short-lived AWS credentials. The trust policy must require the exact repository and protected environment/branch subject; never use a wildcard-only `sub`. PR workflows receive no deployment role. Separate plan and apply roles; apply requires a protected environment approval.
- ECS task execution role is only for image pull, log delivery and secret retrieval at startup. ECS task role is the application runtime identity. They are separate and scoped to required resources/actions.
- Split roles by environment and privilege: read-only plan, state access, artifact publish, deploy, and break-glass administration. Restrict state bucket/KMS permissions to backend operators and CI roles that require them.

## Secret lifecycle

Store database, broker, and cache credentials in AWS Secrets Manager, encrypted by KMS. Terraform uses ephemeral password generation and write-only RDS, ElastiCache, and Secrets Manager arguments; the Amazon MQ CloudFormation template contains a Secrets Manager dynamic reference instead of a password. Secret values are not written to Terraform state or plans. Applications receive only the secret references required by the API task execution role. Do not log secret values or environment dumps. Verify these provider/service semantics again before upgrades; the Amazon MQ password reference is resolved by CloudFormation during broker create/update.

Define an owner, scope, rotation interval, expiry/rotation method, revocation procedure, audit trail and incident response for each credential. Test rotation in a non-production environment, including connection pool refresh/reconnect behavior. Prefer temporary IAM credentials for AWS API calls. ElastiCache supports IAM authentication for compatible Valkey/Redis OSS versions with TLS, but the application client and Spring configuration must be verified before adopting it; password-based AUTH through Secrets Manager is the fallback target.

## Network controls

- ALB is internet-facing and accepts HTTPS only. ACM owns certificates; redirect HTTP to HTTPS if HTTP listener is enabled.
- ECS task security group accepts inbound application traffic only from ALB security group. RDS, cache and broker security groups accept traffic only from ECS task security groups on their exact service ports.
- Data services are private and not publicly accessible. No `0.0.0.0/0` or `::/0` ingress to database/cache/broker.
- Use VPC endpoints and tightly controlled egress where practical. Enable VPC Flow Logs with bounded retention and reviewed access.
- Encrypt storage with service-managed or customer-managed KMS keys according to data classification and recovery requirements. Restrict key administration separately from key use.

## CI/CD policy

Normal CI runs tests and static checks without cloud credentials. Publish artifacts only from trusted refs using least-privilege identity. The deployment role is assumed only by the protected environment workflow. The plan is reviewed before apply and bound to the source SHA; a changed plan must be regenerated and reapproved. Log actor, workflow run, commit, image digest and plan/apply result without logging secret inputs.

## References checked 2026-11-17

- AWS recommends restricting GitHub OIDC trust with a repository/branch/environment-specific `sub`: [Create a role for OIDC federation](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-idp_oidc.html).
- OIDC federation provides temporary role credentials rather than long-lived external credentials: [AWS IAM OIDC federation](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_oidc.html).
- ElastiCache IAM auth requires supported Valkey/Redis OSS versions and in-transit TLS: [IAM authentication](https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/auth-iam.html).
- CloudFormation secret references: [Secrets Manager dynamic references](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/dynamic-references-secretsmanager.html).
- RDS storage encryption: [Encrypting Amazon RDS resources](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.Encryption.html).
