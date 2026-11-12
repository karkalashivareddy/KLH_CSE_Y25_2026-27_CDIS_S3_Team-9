# ADR-009: GitHub Actions OIDC for future cloud deployment identity

- **Status:** Accepted for future deployment design; no cloud identity configured
- **Date:** 2026-11-12
- **Context/problem:** long-lived access keys stored as repository secrets increase impact and rotation risk.
- **Options:** static IAM user keys; OIDC federation with short-lived role; manual deployment only.
- **Decision:** future deployment uses GitHub OIDC, with audience/repository/ref/environment-scoped trust and minimal action permissions. CI validation requires no cloud credential.
- **Justification/advantages:** short-lived credentials, reduced key storage and auditable trust claims.
- **Trade-offs/consequences:** trust policy and workflow permissions must be correct; fork/branch/environment claims require careful scoping; provider setup needs approval.
- **Reconsider when:** institutional policy mandates an approved alternative.
- **Validation/dependencies:** review claims and denied unauthorized refs before first deploy; no role or workflow credentials created.
