# ADR-007: Narrow provider adapters, not universal abstraction

- **Status:** Accepted
- **Date:** 2026-11-12
- **Context/problem:** target comparison spans cloud APIs, but provider semantics and operational features differ.
- **Options:** provider SDKs in domain; one generic cloud framework; small app-owned ports and provider adapters.
- **Decision:** define contracts only at actual application boundaries (object storage, message publishing, secret retrieval, coarse health); retain provider-specific configuration/capabilities in adapters.
- **Justification/advantages:** limits SDK leakage without hiding material semantics; contract tests support evaluation.
- **Trade-offs/consequences:** adapters and compatibility tests cost effort; portability remains partial; no need to implement all providers now.
- **Reconsider when:** a second provider is funded/required or abstraction becomes more complex than direct use.
- **Validation/dependencies:** contract tests against each adapter; map delivery/identity/consistency differences before migration.
