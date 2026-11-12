# Project charter

**Purpose:** authorize and bound the academic engineering effort. **Scope:** Phase 1 product definition and foundations. **Assumptions:** three-person Team 9 as supplied; AWS is a target, not an approved deployment.

## Problem and objective

Cloud-hosted commerce platforms combine application, data, identity, network, operations, recovery, and cost concerns. CloudResilience will provide a contained e-commerce scenario for demonstrating those concerns with measurable engineering evidence. Objectives: define functional/NFR baselines; deliver a small local web/API foundation; design security, data, cloud, and recovery boundaries; and compare portability without claiming production readiness.

## Stakeholders and users

Primary users: shoppers (browse, cart, simulated orders and tracking); administrators (catalog/inventory and operational review); maintainers/operators (health, incidents, recovery exercises, cost review). Stakeholders: the three listed team members, course evaluators, and future test users. No faculty approval is asserted.

## In scope

Requirements, architecture, threat model, initial responsive UI and health/info API, local PostgreSQL/Redis environment, CI foundations, tests, documentation, and later simulated-payment/recovery capabilities.

## Out of scope for Phase 1

Real payments or card data; production customer accounts; public cloud deployments/resources; multi-region failover; actual cloud telemetry; real incident automation; Kubernetes clusters; live Azure/GCP adapters; and production security/compliance certification.

## Constraints and success measures

Local-first; no paid cloud resources without explicit authorization; Java 25/Spring Boot 4.x, React/TypeScript/Vite, PostgreSQL/Redis, Docker Compose, Terraform planned. Proposed Phase 1 acceptance: frontend lint/typecheck/unit/build pass; API Maven tests/package and health route pass; Compose config and service health validated where Docker is available; no credentials committed; docs label future capabilities and state actual validation truthfully.

**Risks:** Java 25/new Spring major dependency compatibility, unavailable Maven/Docker, academic timeline, and fabricated telemetry risk. Mitigations: pin and test; document blockers; avoid premature services; show no invented ops values. Related: [requirements](../02-requirements/REQUIREMENTS_SPECIFICATION.md), ADR-001/002/005.
