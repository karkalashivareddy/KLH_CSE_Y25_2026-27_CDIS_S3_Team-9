# Requirement traceability

**Purpose/scope:** connect requirement intent to architecture and test evidence. Status is current at initial foundation, not a promise. Related documents: requirements specification, acceptance criteria, architecture, test plan.

| IDs | Design location | Planned verification | Current status |
|---|---|---|---|
| FR-001, FR-009 | `apps/web/src/App.tsx`, UI/API boundary | Vitest, browser/API integration | Foundation implemented; tests pending execution |
| FR-002–008 | Future catalog/order modules; data architecture | API, integration, authorization, E2E | Planned |
| FR-010, DR-003/004 | DR architecture/runbooks | isolated timed recovery exercise | Planned |
| FR-011 | `apps/api` health controller/Actuator | Spring MockMvc/security review | Implemented as foundation; test pending execution |
| FR-012 | FinOps strategy/provider adapter | contract and provenance tests | Planned |
| NFR-001–003 | performance/deployment architecture | load, multi-replica, SLI test | Proposed target; not measured |
| NFR-004–005 | CI, UI guidelines | CI/review/accessibility checks | Partially implemented; validation pending |
| SEC-001–006 | security docs, CI, data design | negative tests and scans | Foundation documented; implementation partial |
| INF-001–003 | Compose, deployment/network architecture | Compose validation, IAM/network review | Compose created; validation pending; cloud requirements future |
| DR-001–004 | DR architecture and ADR-008 | restore/game-day evidence | Proposed; not implemented or measured |

**Evidence policy:** attach command output/CI run or test artifact before changing a row to verified. Avoid duplicated requirement IDs; each record owns the full criteria in the linked requirement tables.
