# ADR-034: Local Prometheus, Grafana, Loki, Tempo, and Alloy stack

- **Status:** Accepted for local development
- **Date:** 2026-11-17

## Context

The project needs a repeatable local way to inspect API health and latency, JVM/database/client metrics, asynchronous processing, traces, and structured logs. The observability services should be opt-in and must not be placed on the application’s public edge.

## Decision

Add an opt-in Docker Compose `observability` profile using pinned Prometheus, Grafana, Loki, Tempo, OpenTelemetry Collector, and Grafana Alloy images. Provision data sources, alert rules, and dashboard JSON from version-controlled files. Bind Grafana and Prometheus to host loopback; keep Loki, Tempo, and the Collector on an internal Docker network. Give the API that network only to send traces and expose metrics on its private management listener. Configure Prometheus with seven-day/2 GB retention and Loki with seven-day retention. Keep Alloy Docker discovery restricted to the `cloudresilience` Compose project label.

Alloy requires the Docker Engine socket for local container-log discovery. Mounting the socket read-only does not reduce the API capabilities of the socket itself, so Alloy is a trusted local-development component and must not be treated as a safe production pattern. Platform logging agents with restricted identity should replace it in cloud deployments.

## Alternatives considered

- **Cloud-managed observability only:** rejected for this phase because it would make local telemetry dependent on cloud credentials and paid services.
- **Expose every UI/backend port on all host interfaces:** rejected because local telemetry commonly includes operational details and has no production authentication/TLS setup.
- **Ship all Docker host logs:** rejected because this workspace may run unrelated Compose projects; discovery is filtered to the CloudResilience project.
- **Export metrics by OTLP as well as Prometheus:** not selected; Prometheus scraping is the chosen local metrics path, and the application's OTLP metrics exporter is disabled to avoid duplicate export.

## Consequences

- The profile is easy to start locally and keeps host exposure limited to loopback Grafana and Prometheus.
- Named volumes preserve telemetry between container restarts; stopping the profile does not erase data.
- Local filesystem storage, single replicas, local retention, and unauthenticated internal protocols are unsuitable as production availability/security guarantees.
- Resource consumption can be significant on a developer machine; users should run the profile only when needed.
- CloudWatch or another managed backend may be added through a separately reviewed deployment adapter, without changing domain behavior.

## Validation

The Compose profile was parsed and started locally. API readiness, Prometheus scraping, and trace ingestion into Tempo were observed. The local image/configuration checks do not prove multi-AZ operation, production retention, remote alert delivery, or cloud service integration.
