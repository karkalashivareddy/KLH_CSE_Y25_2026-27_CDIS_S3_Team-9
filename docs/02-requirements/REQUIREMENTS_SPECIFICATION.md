# Requirements specification

**Purpose/scope:** authoritative index for Phase 1 requirements and traceability. **Assumptions:** all numeric operational values below are proposed design targets, not achieved measurements; no production workload or SLO exists.

## Stakeholders and constraints

Shoppers, administrators, operators/maintainers, and evaluators. Constraints: local-first Phase 1; no real payment data, public databases, cloud provisioning, or unsupported claims. See [functional](FUNCTIONAL_REQUIREMENTS.md), [non-functional/security/infrastructure/recovery](NON_FUNCTIONAL_REQUIREMENTS.md), [acceptance](ACCEPTANCE_CRITERIA.md), and [traceability](REQUIREMENT_TRACEABILITY.md).

## Requirement format and priorities

Each record includes ID, description, priority (`Must`/`Should`/`Could`), rationale, acceptance criteria, verification, and dependencies. “Proposed” metrics require workload validation before adoption. `Must` is needed for the defined product foundation; it does not mean implemented.

## Constraints and assumptions

Java 25/Spring Boot 4.x; React/TypeScript; PostgreSQL and Redis; AWS primary target and Azure/GCP comparative target; Docker Compose locally. Target AWS production data services must remain private; no payments beyond simulation. Authentication model, legal retention, regional geography, cloud budget, and workload profile need stakeholder decisions.

**Risk:** ambiguous availability/cost targets can produce misleading claims. No SLO is adopted until workloads, measurement windows, and budget are approved. Related: project charter, system architecture, ADRs.
