# ADR-030: Versioned envelope and allow-listed event contracts

- **Status:** Accepted for Phase 6
- **Context:** Consumers need a stable contract, and ad-hoc JSON maps invite silent breakage and accidental personal data leakage. Event types must also be constrained at the database boundary.
- **Decision:** Publish a single JSON envelope (`eventId`, `eventType`, `eventVersion`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, `causationId`, `payload`) with `eventVersion=1` and aggregate type `CustomerOrder`. Event types and routing keys are an explicit enum and a database check constraint. Payloads are built by dedicated payload builders and include only identifiers, order number, statuses, amounts, currency, item count and a bounded reason. Credentials, session IDs, CSRF tokens, email addresses and payment credentials are excluded. Consumers reject unknown types and non-current versions; unsupported versions are quarantined without retry.
- **Consequences:** Additive fields are safe; breaking changes require a new version and migration plan. A new event type requires a Flyway migration plus an ADR/catalog update. `OrderPaymentSucceeded` is reserved but not emitted in Phase 6.
- **Rejected alternatives:** Untyped maps with no version (no safe evolution); schema registry (no broker ecosystem requirement yet); JSON-LD/CloudEvents envelope (no interop requirement).
