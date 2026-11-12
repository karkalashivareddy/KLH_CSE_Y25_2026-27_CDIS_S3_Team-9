# Functional requirements

All are **planned** unless explicitly shown as current foundation; acceptance criteria define future verification. Verification methods are proposed.

| ID | Requirement | Priority | Rationale | Acceptance criteria | Verification | Dependencies |
|---|---|---|---|---|---|---|
| FR-001 | The web application shall display product identity and truthful local API connectivity state. | Must | Avoid fabricated operations status. | API success, unavailable, and loading states are distinguishable; cloud status never implies deployment. | Component tests + browser review | API health contract |
| FR-002 | The catalog shall allow a shopper to browse and inspect active products. | Must | Core commerce discovery. | Active products are paginated and detail links resolve; unavailable products are not purchasable. | API/component/E2E tests | Product/category data |
| FR-003 | A registered user shall authenticate and receive role-appropriate access. | Must | Protect personal and admin actions. | Invalid/expired credentials are rejected; shopper cannot invoke admin operations. | Security integration tests | Identity/authorization model approval |
| FR-004 | A shopper shall add, update, and remove cart items. | Must | Core pre-order journey. | Quantities validated; unavailable/over-stock items rejected consistently. | Unit/API tests | FR-002, inventory |
| FR-005 | A shopper shall submit an order using a validated address and cart snapshot. | Must | Commerce transaction. | Duplicate submission is idempotent; order and inventory state remain consistent on failure. | Integration/contract tests | FR-003/004, database |
| FR-006 | The system shall simulate payment without accepting or persisting card data. | Must | Safe academic payment scenario. | Only synthetic tokens/scenarios accepted; outcome and audit record correlate to order. | Unit/security tests | FR-005 |
| FR-007 | A shopper shall view order state and tracking history. | Should | Post-purchase visibility. | Only owner or authorized admin can view an order; transitions are recorded. | API authorization tests | FR-003/005 |
| FR-008 | An authorized administrator shall manage products and inventory. | Should | Operable catalog. | Unauthorized mutation denied; stock cannot become negative. | API tests | FR-003 |
| FR-009 | An operator shall view infrastructure/incident status with source and freshness indicated. | Should | Prevent stale or simulated data confusion. | Every observation labels provider, collection time, and simulated/live source; no source means unavailable. | UI/API tests | provider adapter, telemetry |
| FR-010 | An authorized operator shall record and execute a recovery exercise. | Should | Demonstrate DR safely. | Exercise has approver, scope, prechecks, execution log, outcome, and cleanup/rollback record. | Resilience tests | DR approval and isolated environment |
| FR-011 | The project shall expose liveness/readiness status without secret or dependency detail leakage. | Must | Safe operations. | Health paths return expected status and no credentials/internal config. | API integration/security tests | NFR/security |
| FR-012 | Operators shall inspect cost records and resource ownership. | Could | FinOps learning objective. | Records show source/currency/window and are labeled estimates or actuals; unknown values not fabricated. | Contract/UI tests | cloud billing integration |
