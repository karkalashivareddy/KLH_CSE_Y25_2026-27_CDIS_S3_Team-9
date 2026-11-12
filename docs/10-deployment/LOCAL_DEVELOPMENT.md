# Local development

**Purpose/scope:** repeatable local setup; cloud accounts/services are not used. Java 25, Node 24/npm 11 and Docker are required. Maven is required for API development unless using Compose's Maven build stage. Docker Compose v2 plugin required.

## Start

```powershell
Copy-Item .env.example .env   # if not already present; values are local-only
docker compose config
docker compose up --build
```

Open `http://localhost:5173`; API is `http://localhost:8080/api/v1/health`; Actuator is `http://localhost:8080/actuator/health`. PostgreSQL/Redis host ports bind only to loopback. If Docker daemon is unavailable, run web with `cd apps/web; npm ci; npm run dev`; install Maven 3.9.9+ and use `mvn -f apps/api/pom.xml verify` / `mvn -f apps/api/pom.xml spring-boot:run`.

## Common tasks

```powershell
docker compose logs -f api
docker compose down             # preserve named database/cache volumes
docker compose down -v          # destructive local DB/cache reset; inspect before use
cd apps/web; npm ci; npm run lint; npm run typecheck; npm test; npm run build
mvn -f apps/api/pom.xml verify
```

Do not run `down -v` if local data matters. Never use local passwords in hosted environments. The API currently exposes health/info only; PostgreSQL/Redis are started but not wired to business persistence. Troubleshoot Docker daemon status, port conflicts, image pulls, and Java/Maven versions first. Compose/image/test results must be recorded in phase report after actually run.
