# Authentication, sessions and authorization

**Implemented:** email/password customer registration, sign-in, sign-out, current-user endpoint and backend `CUSTOMER`/`ADMIN` access checks. **Not implemented:** email verification, password reset, MFA, login throttling/lockout policy, administrator provisioning, account deletion, external identity provider, shared/distributed session store, or production ingress/TLS.

## Browser/API flow

1. Frontend calls `GET /api/v1/auth/csrf` and receives a CSRF token plus an HttpOnly `XSRF-TOKEN` cookie.
2. Registration/login/logout POST requests include `X-XSRF-TOKEN` (server-provided header name) and same-origin cookies.
3. Successful login authenticates using Spring Security's DAO provider, rotates the session ID, saves the security context to the server session, and clears the prior CSRF token. Frontend fetches a fresh token for subsequent mutations.
4. `GET /api/v1/auth/me` resolves the server session; POST logout invalidates it. A 401 causes the UI to clear its in-memory identity state.

`JSESSIONID` is HttpOnly, SameSite=Lax, and 30-minute idle timeout. `APP_SECURITY_COOKIE_SECURE` defaults false for local HTTP only; production MUST set it true behind HTTPS. Keep frontend/API same-origin; CORS is not enabled. CSRF remains enabled. No bearer token is stored in localStorage or query strings.

## Passwords and registration

BCrypt cost 12 is configured through Spring Security; passwords are never logged or returned. Input must be 12–72 characters and at most 72 UTF-8 bytes. Email is trimmed and lowercased before storage. Errors for unknown email and incorrect password are identical. Registration DTO has no role field; server assigns only CUSTOMER. DB uniqueness resolves concurrent duplicate-registration races.

## Authorization

| Endpoint | Access |
|---|---|
| `GET /api/v1/health`, `/api/v1/info`, `/api/v1/auth/csrf` | Public |
| `POST /api/v1/auth/register`, `/api/v1/auth/login` | Public + CSRF token |
| `POST /api/v1/auth/logout` | Session + CSRF |
| `GET /api/v1/auth/me` | Authenticated |
| `GET /api/v1/admin/access-check` | ADMIN only |

Backend authorization is authoritative. UI route checks do not grant access. There is no initial admin account; do not enable an admin grant until a reviewed provisioning and audit procedure exists. Existing session authorities remain a snapshot until the session ends.

## Operational limitations

Sessions are process-local: API restart invalidates them and multiple instances cannot share them. Before scaling, adopt Spring Session with a properly secured shared store, explicit expiration/revocation behavior, and verified cookie/session-key protections. Production deployment, HTTPS, rate limiting and security review remain required. See ADR-013 through ADR-015.
