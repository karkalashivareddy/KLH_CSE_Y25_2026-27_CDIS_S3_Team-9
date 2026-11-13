# ADR-013: Same-origin server-side sessions for browser authentication

- **Status:** Accepted; session storage evolved by ADR-019
- **Date:** 2026-11-12
- **Context:** the React frontend and API are served same-origin through Vite/Nginx proxies. Browser-held bearer tokens would add refresh/rotation/revocation and XSS exposure without an identified client/API use case.
- **Decision:** use Spring Security's server-side `HttpSession` with an HttpOnly, SameSite=Lax `JSESSIONID`; 30-minute idle timeout, session ID rotation at login, CSRF protection using Spring's cookie token repository and `X-XSRF-TOKEN` header, and CSRF enforcement for all state-changing requests. Logout is POST-only, CSRF-protected and invalidates the session. Tokens are not stored in localStorage or URLs.
- **Cookie transport:** local HTTP sets Secure=false so the Compose/Vite loopback workflow works. Set `APP_SECURITY_COOKIE_SECURE=true` behind production HTTPS. Keep same-origin deployment; no broad CORS policy is configured.
- **Session store:** Redis-backed Spring Session is implemented in Phase 4; see ADR-019 and ADR-022 for namespace, serialization, expiry, multi-instance behavior and outage handling.
- **Rejected alternative:** JWT/refresh tokens add lifecycle complexity and client exposure that are not currently needed.
- **Validation:** integration tests cover login, authenticated identity, customer/admin access and logout invalidation; they run with `-DrunIntegrationTests=true` and Testcontainers/PostgreSQL.
