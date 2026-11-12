# Secrets management

**Purpose/scope:** lifecycle for future runtime credentials; Phase 1 needs none. Local defaults are for isolated development only. See SEC-002, ADR-009.

Never commit `.env`, passwords, private keys, cloud access keys, signing keys, or tokens. `.env.example` values are explicitly public local placeholders and must never be reused in deployed environments. For local environment, store private developer values in ignored `.env`; rotate after exposure. CI should use GitHub OIDC and short-lived role credentials; runtime secrets should use AWS Secrets Manager or a provider-specific managed vault adapter, with narrow IAM and audit. Never pass secrets in command-line arguments, image layers, logs, workflow output, or build artifacts. Establish owner, rotation interval, expiry, revocation and incident procedure per secret. Validation: gitleaks/secret scanner, workflow review, image history review, simulated rotation exercise. No actual secret store/account is configured.
