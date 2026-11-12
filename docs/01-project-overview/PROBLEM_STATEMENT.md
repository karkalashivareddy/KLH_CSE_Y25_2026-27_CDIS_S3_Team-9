# Problem statement

**Purpose/scope:** define the engineering problem rather than assert an existing operational incident. CloudResilience is a proposed academic system, not a response to a verified customer outage.

A realistic cloud-commerce exercise needs to connect user-visible order flows with transactional integrity, asynchronous work, identity boundaries, infrastructure availability, observability, cost controls, and recovery. Studying these concerns in isolation makes it difficult to evaluate trade-offs and verify whether recovery objectives can be met. CloudResilience uses an e-commerce scenario to make the dependencies and failure modes explicit, then adds testable local foundations before any cloud deployment.

The system should demonstrate how a small team can evolve a modular application into a resilient, secure, cost-aware deployment while measuring recovery rather than merely describing it. Phase 1 establishes requirements and a minimal application shell; it does not deliver a complete store, cloud environment, or automated recovery capability.

**Assumptions:** no production users, payment processor, cloud account, budget, or approved service-level commitments are supplied. All proposed performance, availability, RTO/RPO and cost targets remain hypotheses pending workload and course review. Related: charter, NFRs, DR architecture.
