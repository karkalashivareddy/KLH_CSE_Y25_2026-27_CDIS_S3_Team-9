# Local development

**Purpose/scope:** repeatable local setup; cloud accounts/services are not used. Java 25, Node 24/npm 11 and Docker are required. Maven is required for API development unless using Compose's Maven build stage. Docker Compose v2 plugin required.

## Start

```powershell
Copy-Item .env.example .env   # if not already present; values are local-only
docker compose config
docker compose up --build
# Optional second API instance shares PostgreSQL carts and Redis sessions
docker compose --profile two-api up --build
```

Open `http://localhost:5173`; catalog is `/catalog`, customer cart is `/cart`, and the web container reverse-proxies the API. API A is loopback-accessible at `http://localhost:8080`; API B is at `http://localhost:8081` when enabled with `docker compose --profile two-api up --build`. Both share the `api` network alias, and Nginx resolves it dynamically. Readiness is available at each API's `/actuator/health/readiness`. PostgreSQL is loopback-published by default; Redis has no host port. API instances require PostgreSQL and Redis and run Flyway V1–V3. Spring Session uses Redis (`APP_SESSION_REDIS_NAMESPACE`, `APP_SESSION_TIMEOUT`). `.env.example` is local-only; `APP_SECURITY_COOKIE_SECURE=false` is for local HTTP and production must set it true behind HTTPS. `REDIS_PASSWORD` is optional locally and must be a protected deployment secret. This is one local Redis node, not HA. Stop with `docker compose down` to preserve PostgreSQL/cart/session volumes. There is no default administrator account.

## Common tasks

```powershell
docker compose logs -f api
docker compose down             # preserve named database/cache volumes
docker compose down -v          # destructive local database/session reset; inspect before use
cd apps/web; npm ci; npm run lint; npm run typecheck; npm test; npm run build
mvn -f apps/api/pom.xml clean verify
mvn -f apps/api/pom.xml verify -DrunIntegrationTests=true # requires Docker; PostgreSQL/Redis Testcontainers
```

Do not run `down -v` if PostgreSQL/cart/session data matters. Never use local passwords in hosted environments. There is no default admin user. Authentication uses same-origin Spring Session Redis sessions, HttpOnly cookies, session ID rotation, and CSRF tokens. Redis and PostgreSQL are private to Compose networks; Redis has no host port. Configure datasource values, `REDIS_HOST`, `REDIS_PORT`, optional `REDIS_PASSWORD`, `APP_SESSION_TIMEOUT`, and `APP_SESSION_REDIS_NAMESPACE`; connect/read timeouts default to two seconds. Redis outage makes readiness fail and session-backed requests fail closed; there is no in-memory fallback. Compose/image/test results must be recorded in the phase report after actually run.
