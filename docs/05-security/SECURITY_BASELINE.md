# Security baseline

**Purpose/scope:** minimum expected engineering controls; future cloud config requires review. Related: SEC requirements, threat model, `SECURITY.md`.

- Least privilege, deny-by-default authorization, MFA for admin and cloud console.
- No committed credentials; `.env` ignored and examples non-secret; rotate/revoke suspected exposure.
- TLS for external and service traffic; encrypted storage/backups with managed key lifecycle.
- Private database/cache, narrow ingress/egress and separate environments/accounts.
- Parameterized data access, schema validation, bounded requests, secure headers, CSRF/session strategy after auth is selected.
- Safe errors; structured logs with request correlation, no secrets/tokens/card data or unnecessary PII.
- Lockfiles, supported dependencies, vulnerability updates, image/IaC/secret scans and pinned workflow actions.
- CI token minimum permissions, protected review for deployment, short-lived OIDC identity.
- Audit privileged changes and recovery actions; tested backup restoration and incident response.
- Synthetic payment only; never store PAN, CVV, or authentication data.

No certification or compliance claim is made. Validation is per PR and release; exceptions need owner, rationale, expiry and compensating control.
