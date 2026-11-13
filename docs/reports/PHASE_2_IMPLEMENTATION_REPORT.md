# Phase 2 implementation report

**Status: PASS for locally verifiable Phase 2 acceptance gates. Hosted Phase 2 CI is NOT RUN because the branch remains unpushed.**
**Branch:** `feature/phase-2-identity-data` (created from Phase 1 commit `c897782`)
**Commit/push/merge:** none performed, per instruction.

## 1. Executive summary

Phase 2 implements and locally verifies the identity/persistence vertical slice: PostgreSQL configuration, Flyway migration, JPA persistence, customer registration, BCrypt password hashing, server-side sessions, CSRF defenses, backend role authorization, validation/errors, OpenAPI, and frontend account flows. Docker Desktop is available. Testcontainers PostgreSQL migration/persistence/auth tests pass, all Compose services start healthy, and a real browser-origin HTTP smoke flow through Nginx confirms registration, session rotation, login, current-user, customer admin denial, logout, and session invalidation. Local CI-equivalent, secret-scan, and dependency-scan checks pass. Hosted GitHub CI has not run because the branch is still unpushed. This is a local Phase 2 PASS, not a production-readiness claim.

## 2. Architecture decisions

- **Persistence:** PostgreSQL is the system of record; Flyway SQL migrations own schema. Hibernate is `ddl-auto=validate`, with open-session-in-view disabled.
- **Keys:** application-generated UUIDv4 for users; stable UUID role keys seeded by V1. Email is trimmed and lowercased using `Locale.ROOT` and protected by a unique database index.
- **Authentication:** Spring Security server-side `HttpSession`, selected over browser-held JWTs for the same-origin Vite/Nginx topology. Session ID rotates at login; logout invalidates the session. No auth token is written to localStorage or a URL.
- **Cookies/CSRF:** HttpOnly, SameSite=Lax session cookie, 30-minute idle timeout; CSRF remains enabled for register/login/logout. Local Secure=false is only for HTTP development; set `APP_SECURITY_COOKIE_SECURE=true` behind HTTPS.
- **Password hashing:** Spring Security BCrypt, work factor 12; 12–72 characters and at most 72 UTF-8 bytes. Hashes are excluded from public DTOs and `UserPrincipal.toString()`.
- **Authorization:** CUSTOMER and ADMIN are server-side roles. Registration always assigns CUSTOMER and has no role input. No administrator account or default password is created. UI route checks are convenience only; API enforces access.
- Decision records: ADR-011 through ADR-015. See `docs/05-security/AUTHENTICATION_AND_SESSIONS.md`.

## 3. Implemented features

- Registration with validated request DTO, normalized email, unique conflict handling, password encoder, and customer-only role assignment.
- JSON login with generic invalid-credential response, session fixation protection, explicit SecurityContext persistence, and CSRF token rotation.
- CSRF-token endpoint, protected current-user endpoint, POST logout/session invalidation, and ADMIN-only demonstration endpoint.
- Central API error shape with field validation errors but no submitted values or stack traces; generic authentication failures avoid account enumeration.
- Frontend registration, login, actual in-memory identity state sourced from the API session, logout, protected account/admin routes, loading/error states, 401 session clearing, and no fabricated user identity.
- OpenAPI JSON and Swagger UI; identity endpoint contract documented in `docs/10-deployment/API_ENDPOINTS.md`.

## 4. Database schema and migrations

`apps/api/src/main/resources/db/migration/V1__create_identity_schema.sql` creates:

- `users`: UUID PK; normalized unique email; password hash; names; enabled/locked flags; UTC `TIMESTAMPTZ` created/updated values.
- `roles`: UUID PK; unique CUSTOMER/ADMIN name; creation time.
- `user_roles`: composite PK and FK constraints, cascading user deletion, restricted role deletion, and reverse lookup index.

V1 seeds only the two role definitions—no customer/admin accounts. Migrations must be immutable after applying; evolve schema with forward migrations. The API uses externalized datasource URL/user/password, Hikari defaults with 5-second connection timeout, and JDBC connect/socket timeouts. `/api/v1/health` is process status; `/actuator/health/readiness` includes database health. Compose waits on PostgreSQL readiness and keeps PostgreSQL/Redis loopback-bound; Redis is not currently used by the API.

## 5. API endpoints

| Endpoint | Behavior |
|---|---|
| `GET /api/v1/health`, `/api/v1/info` | Public application status and non-sensitive metadata |
| `GET /api/v1/auth/csrf` | Public CSRF header/token bootstrap; CSRF cookie is HttpOnly |
| `POST /api/v1/auth/register` | CSRF-protected; returns 201 and CUSTOMER profile; 409 duplicate |
| `POST /api/v1/auth/login` | CSRF-protected; starts server session; generic 401 on bad credentials |
| `POST /api/v1/auth/logout` | CSRF-protected; invalidates session; returns 204 |
| `GET /api/v1/auth/me` | Authenticated profile and roles; 401 otherwise |
| `GET /api/v1/admin/access-check` | ADMIN only; 401 anonymous / 403 customer |
| `GET /actuator/health/readiness` | DB-aware readiness; health detail values hidden |

All browser requests use the same-origin API path and `credentials: same-origin`. Full request shapes and statuses are in the API endpoint reference.

## 6. Authentication and authorization design

The browser obtains a CSRF token from the server and sends the server-provided header on mutation requests. Login authenticates using Spring Security's DAO provider and BCrypt, rotates the session ID, saves the security context, and clears the previous CSRF token. Frontend stores user display state in memory only; reload revalidates through `/auth/me`. A 401 clears that state and protected pages return to sign-in. Logout is POST-only, CSRF-protected, and invalidates the server session.

Sessions are currently held in one API process. Restart/redeploy invalidates sessions; multiple API replicas cannot share them. Spring Session/shared storage, session revocation on role changes, email verification, password recovery, MFA, throttling/lockout policy, and trusted administrator provisioning are deferred. Do not scale the API horizontally or expose it publicly until those controls and HTTPS are reviewed.

## 7. Security measures

- No JWT or refresh token, no credential/token in localStorage or URL, no cross-origin CORS opening.
- CSRF active for state-changing routes; SameSite cookies and HttpOnly session cookie; production Secure flag documented.
- BCrypt only; UTF-8-byte limit prevents BCrypt truncation; generic credential error; unique DB index handles duplicate race.
- Registration request has no roles field; role grant happens exclusively in server logic. Default API routes are denied unless explicitly permitted/protected.
- Entities are not returned by endpoints; error responses omit internal exceptions, stack traces, and submitted values.
- `.env.example` contains local-only placeholder credentials; no real `.env` is intended to be committed. No cloud credentials/resources/deployment were introduced.
- Local Gitleaks 8.30.1 scanned 130 current project files and found no leaks. This is not a production security review. Phase 2 dependencies were separately checked by the local OSV query below; hosted scans remain pending.
- Local OSV querybatch found two moderate advisories in Testcontainers' transitive `commons-compress:1.24.0`; dependency management now pins the compatible fixed `1.28.0`. Re-scanning 160 resolved Maven coordinates returned zero advisories. Hosted OSV remains pending for this branch.

## 8. Tests executed and actual results

| Check | Result | Evidence |
|---|---|---|
| Web lint/typecheck | PASS | `npm run lint`; `npm run typecheck` |
| Web tests | PASS | `npm test`: 3 files, 13 tests passed (API CSRF/session calls, registration/login/logout/protected UI states) |
| Web production build | PASS | `npm run build`; Vite production output generated |
| npm audit | PASS | `npm audit`: 0 vulnerabilities |
| API full verification | PASS | `mvn -B -ntp -f apps/api/pom.xml clean verify -DrunIntegrationTests=true`: BUILD SUCCESS; 10 tests, 0 failures/errors/skips |
| API test breakdown | PASS | 4 health tests, 2 PostgreSQL Testcontainers end-to-end tests, 3 registration unit tests, 1 principal/account-state test |
| PostgreSQL/Flyway/schema validation | PASS | Testcontainers started PostgreSQL 17.7, Flyway validated and applied V1, Hibernate `ddl-auto=validate` initialized, user and role repository operations passed |
| Authentication/API security integration | PASS | Testcontainers proved validated registration, normalized/unique email, bcrypt persistence, server-only CUSTOMER grant despite submitted ADMIN, bad login rejection, session-backed `/me`, customer 403, admin-fixture 200, CSRF-protected logout, post-logout 401 |
| Compose build/startup | PASS | `docker compose up --build -d`; API, web, PostgreSQL and Redis all reported healthy; DB-aware readiness HTTP 200 |
| Live frontend-to-backend flow | PASS | Through `http://127.0.0.1:5173` and Nginx proxy: web 200, registration 201 CUSTOMER, login 200, `/me` 200, CUSTOMER admin request 403, logout 204, `/me` after logout 401; observed JSESSIONID rotated at login |
| Compose/PostgreSQL state | PASS | `docker compose config --quiet`; `flyway_schema_history` version 1; CUSTOMER and ADMIN roles exist. Compose services are left running for local review; smoke-test accounts remain in the local named volume. |
| Local secret scan | PASS | Gitleaks 8.30.1 current-worktree scan: 130 project files, 0 leaks |
| Phase 2 Maven vulnerability scan | PASS (local OSV query) | OSV checked 160 resolved coordinates, 0 advisories after fixing two test-scope `commons-compress` advisories; hosted OSV remains pending. |
| Hosted Phase 2 CI | NOT RUN | Changes are uncommitted/unpushed on the Phase 2 feature branch. Phase 1 main CI passed; it is not evidence for Phase 2. |

Maven emits Guice/JVM warnings (including deprecated `sun.misc.Unsafe` and class-data-sharing notices); they did not fail verification. The Docker build emitted non-fatal Node engine warnings because its pinned Node 24.13 image is below the declared engine range of a nested test dependency; the image build and frontend build passed. Consider aligning the Docker Node image to the repository's supported Node 24.19 before a later release.

## 9. CI verification status

`.github/workflows/ci.yml` runs frontend checks and invokes `mvn -B -ntp -f apps/api/pom.xml verify -DrunIntegrationTests=true` on GitHub runners, enabling the PostgreSQL Testcontainers suite. Workflow YAML parsed locally; pinned Gitleaks and OSV jobs remain. **No Phase 2 hosted run exists** because changes are uncommitted and unpushed. Run/inspect hosted checks only after the user authorizes a push. No cloud credentials or deployment jobs were added.

## 10. Files created or modified

**Modified:** `.env.example`; `.github/workflows/ci.yml`; `README.md`; `compose.yaml`; `apps/api/pom.xml`; `apps/api/src/main/resources/application.yml`; `apps/api/src/main/java/com/cloudresilience/modules/health/HealthController.java`; `apps/api/src/test/java/com/cloudresilience/modules/health/HealthControllerTest.java`; `apps/web/src/App.tsx`; `apps/web/src/api.ts`; `apps/web/src/api.test.ts`; `apps/web/src/styles.css`; `docs/03-architecture/DATABASE_DESIGN.md`; `docs/03-architecture/ENTITY_RELATIONSHIPS.md`; `docs/04-architecture-decisions/ADR-003-postgresql.md`; `docs/05-security/IDENTITY_AND_ACCESS_MODEL.md`; `docs/09-testing/TEST_STRATEGY.md`; `docs/10-deployment/LOCAL_DEVELOPMENT.md`; `docs/reports/PHASE_1_CLOSURE_VERIFICATION.md`.

**Created:** `apps/api/src/main/java/com/cloudresilience/OpenApiConfiguration.java`; `apps/api/src/main/java/com/cloudresilience/security/ApiErrorWriter.java`; `apps/api/src/main/java/com/cloudresilience/security/SecurityConfiguration.java`; identity classes under `apps/api/src/main/java/com/cloudresilience/modules/identity/{api,application,domain,persistence}/` (`ApiError`, `ApiExceptionHandler`, `AuthController`, `InvalidCredentialsException`, `LoginRequest`, `RegisterRequest`, `UserResponse`; `DatabaseUserDetailsService`, `EmailAlreadyRegisteredException`, `InvalidRegistrationException`, `RegistrationService`, `UserPrincipal`; `RoleName`; `RoleEntity`, `RoleRepository`, `UserEntity`, `UserRepository`); `apps/api/src/main/resources/db/migration/V1__create_identity_schema.sql`; `apps/api/src/test/java/com/cloudresilience/modules/identity/IdentityIntegrationTest.java`; `apps/api/src/test/java/com/cloudresilience/modules/identity/RegistrationServiceTest.java`; `apps/api/src/test/java/com/cloudresilience/modules/identity/UserPrincipalTest.java`; `apps/web/src/auth.tsx`; `apps/web/src/auth-context.ts`; `apps/web/src/auth-flow.test.tsx`; `docs/03-architecture/IDENTITY_SCHEMA.md`; `docs/04-architecture-decisions/ADR-011-identity-primary-keys.md`; `docs/04-architecture-decisions/ADR-012-flyway-migrations.md`; `docs/04-architecture-decisions/ADR-013-cookie-session-authentication.md`; `docs/04-architecture-decisions/ADR-014-password-hashing.md`; `docs/04-architecture-decisions/ADR-015-identity-rbac.md`; `docs/05-security/AUTHENTICATION_AND_SESSIONS.md`; `docs/10-deployment/API_ENDPOINTS.md`; `docs/reports/PHASE_2_IMPLEMENTATION_REPORT.md`.

## 11. Known issues and deferred work

1. Hosted Phase 2 CI has not run; the branch is unpushed. Do not call hosted CI passed.
2. Sessions are process-local and lost on restart; before multiple replicas, adopt/test a secure shared session store and revocation plan.
3. No admin provisioning workflow or admin account exists. Establish an audited, least-privilege process before production ADMIN use.
4. Email verification, password reset, MFA, login throttling/lockout, user management, audit events, account deletion/retention, Redis application integration, products, inventory, cart, orders, payments and cloud deployment remain deferred.
5. Production security review, browser E2E automation, accessibility audit, load test and recovery exercise have not run. Docker emitted non-fatal Node engine-range warnings during image build.

## 12. Exact local commands

```powershell
# Frontend
cd apps/web
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npm audit

# API independent checks; Docker integration suite is skipped unless opted in
cd ../..
mvn -B -ntp -f apps/api/pom.xml clean verify

# Requires Docker Desktop / Docker-compatible runtime and image access
mvn -B -ntp -f apps/api/pom.xml verify -DrunIntegrationTests=true
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

The above Compose/API smoke flow was executed successfully in this environment. Services are intentionally left running at `http://localhost:5173` with persistent local data. To stop while preserving local account data, run `docker compose down`; do not use `docker compose down -v` unless you explicitly want to delete the PostgreSQL/Redis named volumes. Configure local `.env` only with non-production values.

## 13. Readiness for next milestone

**PASS for Phase 2 local definition-of-done gates; hosted CI pending.** PostgreSQL persistence/migration, registration/login/logout, customer/admin authorization, CSRF/session behavior, real frontend-to-backend flow, Phase 1 regression checks, documentation, and local security/dependency scans have evidence above. Hosted GitHub CI must still be run after an authorized push; no commit, push, or merge was performed. No production/cloud readiness is claimed.

## Post-push and Phase 4 status correction

This document records the Phase 2 implementation-time snapshot. Phase 2 commit `14a4dec` was later explicitly pushed to `main`; hosted CI run `43243992683` passed. The process-local session limitation stated in Sections 6 and 11 was superseded in source by Phase 4 Spring Session/Redis implementation on the local `feature/phase-4-distributed-state-cart` branch. See [`PHASE_4_IMPLEMENTATION_REPORT.md`](PHASE_4_IMPLEMENTATION_REPORT.md): distributed behavior is implemented but its Docker-dependent integration verification is BLOCKED in this environment. This addendum does not claim Phase 4 production readiness.
