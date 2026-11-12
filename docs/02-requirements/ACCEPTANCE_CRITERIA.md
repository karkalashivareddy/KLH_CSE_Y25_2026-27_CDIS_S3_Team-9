# Acceptance criteria

**Purpose/scope:** testable product outcomes for Phase 1 foundation and later increments. Targets in NFRs are proposals, not achieved. Related: functional/NFR tables and traceability matrix.

## Phase 1 foundation

- `AC-P1-01`: clean dependency installation produces a frontend lint, typecheck, Vitest, and production build result (pass/fail recorded individually).
- `AC-P1-02`: API `GET /api/v1/health` returns HTTP 200 JSON with status `UP`; `/api/v1/info` exposes only safe static metadata.
- `AC-P1-03`: Compose config validates; if Docker daemon is available, all local services become healthy and web reaches API; database binds only loopback on host and is not attached to application network.
- `AC-P1-04`: no cloud provider is presented as connected and no operational metric is represented as live without source metadata.
- `AC-P1-05`: repository CI runs web checks and backend tests/package with minimum token permissions and no cloud credentials.
- `AC-P1-06`: requirements, architecture/security documents, audit and handoff accurately distinguish implemented vs planned.
- `AC-P1-07`: secret scan finds no credentials; any false positive is investigated and recorded.

## Product acceptance (future)

- An unauthorized caller cannot read another user's cart/order or invoke administrator actions (FR-003/004/007/008; test access matrix).
- Order creation snapshots valid item price/quantity, reserves stock consistently, tolerates duplicate request idempotently, and creates no card data (FR-005/006; transactional integration tests).
- Simulated payment outcomes are deterministic for test scenarios and do not call a real processor (FR-006).
- Product and infrastructure views identify stale/missing provider readings rather than substituting fake values (FR-009/012).
- Recovery runs have approval, prechecks, audit, observable completion, and safe rollback/cleanup (FR-010, DR-003).

**Validation:** link each test/report evidence in traceability; no criterion is marked passed without retained output.
