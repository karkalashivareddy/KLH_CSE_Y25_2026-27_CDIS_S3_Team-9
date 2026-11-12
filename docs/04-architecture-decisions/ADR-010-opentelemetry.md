# ADR-010: OpenTelemetry as the instrumentation boundary

- **Status:** Accepted as planned; instrumentation not implemented
- **Date:** 2026-11-12
- **Context/problem:** target spans application and multiple provider monitoring products; telemetry must correlate without binding domain code to one vendor.
- **Options:** provider SDK only; OpenTelemetry APIs/exporters; logs only.
- **Decision:** use OpenTelemetry API/SDK and semantic conventions where supported; export to selected backend via controlled collector/gateway. Keep sensitive attribute allowlists.
- **Justification/advantages:** common instrumentation and ecosystem; provider backend remains a deployment choice.
- **Trade-offs/consequences:** collector/exporter and cardinality costs; semantic conventions evolve; OTel does not make vendor backends interchangeable.
- **Reconsider when:** operational burden or platform support makes direct instrumentation demonstrably better.
- **Validation/dependencies:** trace context propagation, redaction, sampling and failure/cost tests. No OTel dependency/deployment in Phase 1.
