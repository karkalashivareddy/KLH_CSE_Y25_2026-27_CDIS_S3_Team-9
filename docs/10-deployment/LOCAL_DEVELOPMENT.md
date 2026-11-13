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

Open `http://localhost:5173`; catalog is `/catalog`, customer cart is `/cart`, orders are `/orders`, and the web container reverse-proxies the API. API A is loopback-accessible at `http://localhost:8080`; API B is at `http://localhost:8081` when enabled with `docker compose --profile two-api up --build`. Both share the `api` network alias, and Nginx resolves it dynamically. Readiness is available at each API's `/actuator/health/readiness`; `/actuator/health/messaging` reports outbox and broker state. PostgreSQL is loopback-published by default; Redis and RabbitMQ are private to the Compose data network, with RabbitMQ AMQP/management loopback-published for inspection. API instances require PostgreSQL, Redis and RabbitMQ and run Flyway V1–V5. Spring Session uses Redis (`APP_SESSION_REDIS_NAMESPACE`, `APP_SESSION_TIMEOUT`). Order lifecycle changes write to the PostgreSQL outbox, and a scheduled publisher drains it to RabbitMQ; consumers persist in-app notifications and an activity projection. `.env.example` is local-only; `APP_SECURITY_COOKIE_SECURE=false` is for local HTTP and production must set it true behind HTTPS. `REDIS_PASSWORD` and `RABBITMQ_PASSWORD` are optional locally and must be protected deployment secrets. These are single local nodes, not HA. Stop with `docker compose down` to preserve PostgreSQL/Redis/RabbitMQ volumes. There is no default administrator account.

## Common tasks

```powershell
docker compose logs -f api
docker compose down             # preserve named database/cache volumes
docker compose down -v          # destructive local database/session reset; inspect before use
cd apps/web; npm ci; npm run lint; npm run typecheck; npm test; npm run build
mvn -f apps/api/pom.xml clean verify
mvn -f apps/api/pom.xml verify -DrunIntegrationTests=true # requires Docker; PostgreSQL/Redis/RabbitMQ Testcontainers
```

Do not run `down -v` if PostgreSQL/cart/order/event data matters. Never use local passwords in hosted environments. There is no default admin user. Authentication uses same-origin Spring Session Redis sessions, HttpOnly cookies, session ID rotation, and CSRF tokens. Redis, RabbitMQ and PostgreSQL are private to Compose networks; only loopback ports are published. Configure datasource values, `REDIS_HOST`, `REDIS_PORT`, optional `REDIS_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `APP_SESSION_TIMEOUT`, and `APP_SESSION_REDIS_NAMESPACE`; connect/read timeouts default to two seconds. Redis outage makes readiness fail and session-backed requests fail closed; there is no in-memory fallback. A RabbitMQ outage does not fail readiness: committed outbox rows remain `PENDING` and drain after recovery. `APP_MESSAGING_ENABLED=false` disables listeners for broker-free API tests; `APP_OUTBOX_PUBLISHER_ENABLED=false` disables the publisher. Compose/image/test results must be recorded in the phase report after actually run.
