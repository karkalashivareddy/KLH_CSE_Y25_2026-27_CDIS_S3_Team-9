# Identity and access model

**Purpose/scope:** target authorization model; actual login/roles are not implemented. **Assumptions:** shopper/admin/operator roles are initial candidates only.

| Principal | Minimum access |
|---|---|
| Anonymous shopper | Read published catalog and public health only; no personal/order data. |
| Authenticated shopper | Own profile/cart/orders only; no admin functions. |
| Catalog administrator | Catalog/inventory operations; no cloud recovery privileges by default. |
| Operator | Read operational status; recovery execution requires explicit scoped approval. |
| CI validation | Read repository and publish test artifacts only; no cloud permissions. |
| Future deploy job | OIDC role scoped to repository, protected ref/environment, audience and specific deployment actions. |
| Break-glass | MFA, time-bound, dual approval where possible, audited and reviewed after use. |

Authentication provider/session/token model is a deferred ADR; never invent a local password flow for production. Enforce object ownership and function authorization on server. Review role grants and revoke on membership changes. Validation: threat model, access matrix negative tests, IAM policy review.
