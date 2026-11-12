# Local development

**Purpose/scope:** repeatable local setup; cloud accounts/services are not used. Java 25, Node 24/npm 11 and Docker are required. Maven is required for API development unless using Compose's Maven build stage. Docker Compose v2 plugin required.

## Start

```powershell
Copy-Item .env.example .env   # if not already present; values are local-only
docker compose config
docker compose up --build
```

Open `http://localhost:5173`; the public catalog is `/catalog` and the API is `http://localhost:8080/api/v1/health`; DB-aware readiness is `http://localhost:8080/actuator/health/readiness`. Copy root `.env.example` to `.env` for local-only settings; change its local-only database password if needed. The API requires configured PostgreSQL and runs Flyway V1/V2 before serving. PostgreSQL/Redis host ports bind only to loopback. `APP_SECURITY_COOKIE_SECURE=false` is for local HTTP only; set true behind HTTPS outside local development. Redis is started but not used by the API. There is no default administrator account; use a deliberately provisioned local ADMIN account to exercise admin screens. If Docker daemon is unavailable, run frontend with `cd apps/web; npm ci; npm run dev`, but API needs a reachable PostgreSQL instance; set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`, then use Maven 3.9.11+ with `mvn -f apps/api/pom.xml spring-boot:run`.

## Common tasks

```powershell
docker compose logs -f api
docker compose down             # preserve named database/cache volumes
docker compose down -v          # destructive local DB/cache reset; inspect before use
cd apps/web; npm ci; npm run lint; npm run typecheck; npm test; npm run build
mvn -f apps/api/pom.xml clean verify
mvn -f apps/api/pom.xml verify -DrunIntegrationTests=true # requires Docker; PostgreSQL Testcontainers, including inventory concurrency
```

Do not run `down -v` if local data matters. Never use local passwords in hosted environments. There is no default admin user. Authentication uses same-origin server-side sessions, HttpOnly cookies, and CSRF tokens; a single API process stores sessions in memory, so restarts log users out. Compose/image/test results must be recorded in the phase report after actually run.
