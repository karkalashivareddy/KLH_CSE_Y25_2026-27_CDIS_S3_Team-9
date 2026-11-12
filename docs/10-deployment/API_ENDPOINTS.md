# API endpoint reference

Base path: `/api/v1`. OpenAPI JSON is available at `/v3/api-docs`; local Swagger UI is `/swagger-ui.html`. All browser POSTs require the CSRF header token returned by `GET /api/v1/auth/csrf`. Cookies are same-origin. Error responses use `timestamp`, `status`, `error`, generic `message`, `path`, and `fieldErrors`; they do not expose stack traces or submitted values.

| Method and path | Access | Success |
|---|---|---|
| `GET /api/v1/health` | Public | 200; application process status, not DB readiness |
| `GET /api/v1/info` | Public | 200; non-sensitive app metadata |
| `GET /api/v1/auth/csrf` | Public | 200; `{headerName, token}`, sets HttpOnly CSRF cookie |
| `POST /api/v1/auth/register` | Public + CSRF | 201; normalized user profile with CUSTOMER role |
| `POST /api/v1/auth/login` | Public + CSRF | 200; user profile, starts server-side session |
| `POST /api/v1/auth/logout` | Session + CSRF | 204; invalidates session |
| `GET /api/v1/auth/me` | Authenticated | 200; current profile and roles; 401 otherwise |
| `GET /api/v1/admin/access-check` | ADMIN | 200; demonstration authorization result; 401/403 otherwise |
| `GET /actuator/health/readiness` | Public | DB-aware readiness; health details are hidden |
| `GET /api/v1/categories[/{id}]` | Public | Active categories only |
| `GET /api/v1/products?page=0&size=20&sort=name&direction=asc&categoryId=&q=` | Public | Active products, optional filter/search, DB pagination; size <= 100 |
| `GET /api/v1/products/{id}` | Public | Active product detail with derived availability |
| `GET /api/v1/products/{id}/availability` | Public | Derived quantity on hand minus reserved |
| `POST /api/v1/admin/categories`, `PUT /api/v1/admin/categories/{id}` | ADMIN + CSRF | Create/update flat categories |
| `POST /api/v1/admin/products`, `PUT /api/v1/admin/products/{id}` | ADMIN + CSRF | Create/update products; creation starts DRAFT |
| `PATCH /api/v1/admin/products/{id}/status` | ADMIN + CSRF | Activate/archive product lifecycle status |
| `POST /api/v1/admin/inventory/{productId}` | ADMIN + CSRF | Initialize stock once; append initial movement |
| `GET /api/v1/admin/inventory[/{productId}]` | ADMIN | Paginated stock list or stock plus latest 200 movements |
| `POST /api/v1/admin/inventory/{productId}/adjustments` | ADMIN + CSRF | Signed non-zero `delta`, required reason; cannot reduce below reserved |
| `POST /api/v1/admin/inventory/{productId}/reservations` | ADMIN + CSRF | Positive quantity; serialized check-and-reserve; returns reservation ID |
| `POST /api/v1/admin/inventory/{productId}/reservations/{reservationId}/release` | ADMIN + CSRF | Release a RESERVED record once |

Catalog product pages use `content`, `page`, `size`, `totalElements`, and `totalPages`. Sort fields are allowlisted (`name`, `sku`, `price`, `createdAt`); direction is `asc`/`desc`, with ID tie-breaking. Search is case-insensitive name/SKU substring matching. Inventory availability is derived; customers cannot mutate inventory or reservations. Mutation errors use the common error envelope: 400 validation, 401 unauthenticated, 403 non-admin, 404 unknown/unpublished resources, 409 uniqueness/lifecycle/insufficient-stock conflicts.

Registration JSON:

```json
{"email":"person@example.com","password":"at-least-twelve-characters","firstName":"Ada","lastName":"Lovelace"}
```

No role/permission field is accepted by the registration contract. A duplicate normalized email returns 409. Invalid request DTOs return 400. Login returns one generic 401 message for unknown email, wrong password, disabled, or locked accounts. No production administrator provisioning API exists.
