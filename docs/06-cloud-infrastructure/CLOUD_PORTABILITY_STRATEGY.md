# Cloud portability strategy

**Purpose/scope:** bound portability effort; no cloud adapters/deployments are implemented. Avoid treating multi-cloud as free or complete portability.

Keep business logic independent of provider SDKs. Introduce narrow contracts only where the application needs interchangeable behavior: `ObjectStorage`, `MessagePublisher`, `SecretProvider`, and coarse `CloudHealthProvider`. Put provider-specific identity, retry, delivery, encryption, and lifecycle semantics in adapters. Preserve capabilities via explicit optional capability/config rather than obscure them in a generic abstraction. PostgreSQL portability is higher at SQL level but extensions, HA and operational behavior differ. Containers provide packaging portability, not network/identity/billing portability.

Test contract semantics and provider-specific integration independently. Maintain migration inventory for identities, data transfer, encryption keys, events, observability and DNS. Weigh egress, dual-running, skill, support, reliability and operational cost against portability benefits. Revisit abstractions when a second provider has a funded test/deployment use case; do not prebuild a framework.
