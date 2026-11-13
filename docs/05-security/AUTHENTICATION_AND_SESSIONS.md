# Authentication, sessions and authorization

**Implemented:** email/password customer registration, sign-in, sign-out, current-user endpoint, backend `CUSTOMER`/`ADMIN` access checks, and Redis-backed shared sessions. **Not implemented:** email verification, password reset, MFA, login throttling/lockout policy, administrator provisioning, account deletion, external identity provider, or production ingress/TLS.

## Browser/API flow

1. Frontend calls `GET /api/v1/auth/csrf` and receives a CSRF token plus an HttpOnly `XSRF-TOKEN` cookie.
2. Registration/login/logout POST requests include `X-XSRF-TOKEN` (server-provided header name) and same-origin cookies.
3. Successful login authenticates using Spring Security's DAO provider, rotates the session ID, saves the security context to the server session, and clears the prior CSRF token. Frontend fetches a fresh token for subsequent mutations.
4. `GET /api/v1/auth/me` resolves the server session; POST logout invalidates it. A 401 causes the UI to clear its in-memory identity state.

`JSESSIONID` is HttpOnly, SameSite=Lax, path `/`, and 30-minute idle timeout; the session cookie is not given a separate browser Max-Age. `APP_SECURITY_COOKIE_SECURE` defaults false for local HTTP only; production MUST set it true behind HTTPS. Spring Session stores session records in Redis under a configurable namespace; the SecurityContext principal is serializable but its password hash and authentication credentials are removed before session persistence. Keep frontend/API same-origin; CORS is not enabled. CSRF remains enabled. No bearer token or session ID is stored in localStorage or query strings.

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

Redis is a required shared session dependency: API replicas can resolve and invalidate the same session; logout invalidates it across replicas, and expiration is enforced server-side. Redis is not host-published in Compose. Readiness includes Redis, and there is no process-local fallback; outages fail closed with readiness down and session-backed requests returning a generic unavailable error where safe to do so. Local Compose Redis with an AOF volume is not HA or a backup. A Redis data loss forces reauthentication. Existing session authorities are snapshots until the session ends. Production deployment, TLS, Redis authentication/network policy, rate limiting and security review remain required. See ADR-013 through ADR-015 and ADR-019/022.
