# Phase 7 observability and SRE implementation report

**Status:** Local implementation and verification pass. Hosted CI is pending the direct `main` push.

**Date:** 2026-11-17  
**Branch:** `main` (repository-owner directed; no PRs)  
**Base commit:** `b14d44f` (Phase 6 integrated `main`)  
**Implementation commit:** pending  
**CI run:** pending

## Delivered

- Spring Boot OpenTelemetry tracing and Prometheus registry; W3C Trace Context/baggage, configurable sampling, and OTLP trace export.
- Separate management listener in Compose (`8081`); only the application listener is host-published. Prometheus scrapes metrics over the internal observability network.
- Low-cardinality custom metrics for checkout outcome, order transitions, outbox state/age/publication, consumer latency/retries, and DLQ depth. Order, customer, message, and session IDs are not metric labels.
- Spring AMQP template/listener observations enabled. A RabbitMQ Testcontainers case verifies W3C `traceparent` propagation in published message headers.
- Version-pinned local profile: Prometheus, Grafana, Loki, Tempo, OpenTelemetry Collector, and Alloy. Eight provisioned dashboards, seven Prometheus alerts, local retention limits, loopback-only Grafana/Prometheus access, and project-scoped Docker log discovery.
- Bounded k6 catalog-browsing scenario and reproducible local run command.

## Verification

| Check | Result | Evidence |
|---|---|---|
| Backend full suite: `& (Join-Path (Join-Path $env:TEMP 'apache-maven-3.9.11') 'bin\mvn.cmd') -B -ntp '-Dmaven.repo.local=C:\Users\karka\.m2\repository' -f apps/api/pom.xml verify -DrunIntegrationTests=true` | **PASS** | 66 tests, 66 passed, 0 failures, 0 errors, 0 skipped; Testcontainers with PostgreSQL 17.7, Redis 8.4, RabbitMQ 4.0; Java 25, Docker 29.7.2 |
| RabbitMQ trace propagation: `& (Join-Path (Join-Path $env:TEMP 'apache-maven-3.9.11') 'bin\mvn.cmd') -B -ntp '-Dmaven.repo.local=C:\Users\karka\.m2\repository' -f apps/api/pom.xml test -DrunIntegrationTests=true '-Dtest=EventDrivenIntegrationTest'` | **PASS** | 7 tests, 0 failures/errors/skips, including W3C trace header case |
| Frontend lint/typecheck/tests/build | **PASS** | Lint and typecheck clean; 21 tests in 6 files; Vite build completed |
| `npm.cmd audit --audit-level=moderate` | **PASS** | 0 vulnerabilities |
| Compose config, both standard and observability/load-test profiles | **PASS** | `docker compose ... config --quiet` |
| Local Compose stack | **PASS** | API, PostgreSQL, Redis, RabbitMQ, Prometheus, Grafana, Loki, Tempo, Collector, Alloy healthy/running; API readiness healthy |
| Prometheus target and alert rules | **PASS** | Internal API scrape target is `up`; `promtool check rules` found 7 valid rules; all alert rule health states `ok` |
| Dashboards | **PASS** | Grafana health API returned 200 and listed 8 provisioned dashboards |
| Private metrics listener | **PASS** | Host-published API port does not serve `/actuator/prometheus`; internal Prometheus scrape succeeds |
| Trace ingestion | **PASS** | Tempo returned real API/scheduled-task traces; RabbitMQ propagation integration test emits `traceparent` |
| k6 catalog scenario | **PASS** | 5 VUs, 30 seconds, 150 requests, 4.9386 requests/s, 0% failures, p50 8.6669 ms, p95 21.1790 ms, p99 29.3967 ms |
| `git diff --check` and dashboard JSON parsing | **PASS** | Clean whitespace; all 8 JSON dashboard files parse |
| Hosted API/Web, Gitleaks, OSV checks | **NOT RUN** | To run after direct push to `main`; update this report with the Actions URL and exact tested SHA |

## Corrections during verification

- The first full Maven run exposed a regression when the management-port default was set globally. That setting was removed from application defaults and confined to Compose; the complete suite then passed.
- Tempo 3.1 rejects the removed Tempo 2 `ingester`/`compactor` config blocks. The configuration was updated for Tempo 3 monolithic mode and the service started successfully.
- RabbitMQ and Lettuce dashboard panels were aligned to metric names present in the live Prometheus registry.
- Alloy Docker discovery is filtered by the `cloudresilience` Compose project label. A new filtered Loki volume is used; the earlier local Loki volume is left untouched and no longer mounted.
- Grafana and Prometheus use a dedicated local host-access network for loopback ports. Application and backend observability services remain on the internal network.

## Limitations and not verified

- The k6 measurement is one local catalog-only run; it does not measure authenticated flows, checkout, consumer throughput, CPU/memory, database saturation, or cloud performance.
- Browser-side performance/error telemetry is not implemented in this phase.
- Consumer span linking and trace continuity through retries and DLQ replay have not been independently verified; the outbound RabbitMQ W3C header is covered by an integration test.
- SLO values are proposed targets. No production SLO history or alert receiver exists.
- No cloud deployment or paid resource provisioning occurred.
- No cloud-scale load test or production penetration test was performed.

## Security scans and Git state

- npm audit: **PASS**, 0 vulnerabilities.
- Gitleaks and OSV: **NOT RUN locally in this phase**; hosted checks are pending.
- No credentials are committed. `.env` remains ignored. Grafana's tracked example password is a local placeholder only.
- Work is on `main` per the user's instruction. No feature branch or PR was created. No cloud resource was provisioned.
- Final commit SHA, hosted CI URL/status, and working-tree state will be appended after the push and Actions run.
