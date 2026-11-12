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

Registration JSON:

```json
{"email":"person@example.com","password":"at-least-twelve-characters","firstName":"Ada","lastName":"Lovelace"}
```

No role/permission field is accepted by the registration contract. A duplicate normalized email returns 409. Invalid request DTOs return 400. Login returns one generic 401 message for unknown email, wrong password, disabled, or locked accounts. No production administrator provisioning API exists.
