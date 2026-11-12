# Identity and access model

**Purpose/scope:** Phase 2 implements customer login and CUSTOMER/ADMIN role storage/checks; operator/recovery roles remain target assumptions. See [authentication and sessions](AUTHENTICATION_AND_SESSIONS.md) and ADR-015.

| Principal | Minimum access |
|---|---|
| Anonymous shopper | Read published catalog and public health only; no personal/order data. |
| Authenticated shopper | Own profile/cart/orders only; no admin functions. |
| ADMIN | Demonstration endpoint only; no catalog/inventory or cloud recovery API is implemented. |
| Operator | Future role; read operational status; recovery execution would require explicit scoped approval. |
| CI validation | Read repository and publish test artifacts only; no cloud permissions. |
| Future deploy job | OIDC role scoped to repository, protected ref/environment, audience and specific deployment actions. |
| Break-glass | MFA, time-bound, dual approval where possible, audited and reviewed after use. |

Production identity provider, shared session store, operational admin provisioning, MFA, object ownership, role revocation and cloud IAM integration remain deferred. Enforce object ownership and function authorization on server as product modules are added. Review role grants and revoke sessions on membership changes. Validation: threat model, access matrix negative tests, and IAM policy review.
