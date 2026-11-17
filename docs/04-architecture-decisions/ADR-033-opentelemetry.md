# ADR-033: OpenTelemetry tracing and W3C context

- **Status:** Accepted for local implementation
- **Date:** 2026-11-17

## Context

CloudResilience uses a modular Spring Boot API with PostgreSQL transactions and RabbitMQ-based downstream processing. Diagnosing a request that triggers asynchronous work requires trace context and instrumentation across supported boundaries. Instrumentation must not add personal or secret data to telemetry, and the application must remain usable without a local tracing backend.

## Decision

Use the Spring Boot OpenTelemetry starter and OTLP export to an OpenTelemetry Collector. Configure W3C Trace Context propagation, a configurable sampling probability (default 10%), and bounded span attribute count/value size. Keep export disabled by default and enable it in the local observability Compose profile. Enable Spring AMQP template/listener observations so Rabbit operations can be instrumented. Keep metrics collection separate through Prometheus scraping of the Actuator endpoint.

## Consequences

- The Collector provides a stable application-facing OTLP destination and forwards traces to Tempo in the local profile.
- Request, scheduled-task, JDBC/Micrometer, and message operations can share trace context where instrumentation is active.
- Operators can turn tracing off when no collector is available; routine application liveness does not depend on Tempo.
- Sampling means traces are diagnostic samples, not a complete audit record. Business audit history remains in PostgreSQL.
- Retry and DLQ replay context continuity has not been tested end to end. Replay should be treated as a new operational trace linked with explicit correlation/causation metadata rather than assuming the original span is still active.
- The local OTLP network is internal to Compose and is not a production security boundary. Production requires authenticated/encrypted transport and retention/access policies appropriate to the deployment.

## Validation

Local stack verification observed API traces in Tempo after HTTP and scheduled processing activity. This does not verify every asynchronous retry/replay path or a deployed cloud collector.
