# Product vision

**Purpose/scope:** guide product increments across the course project. **Vision:** enable a team to explore how a commerce application remains trustworthy and recoverable as infrastructure fails, while making security and cost visible design constraints.

Shoppers should eventually browse products, manage a cart, place simulated-payment orders, and follow order progress. Administrators should maintain catalog and inventory. Operators should inspect system health and incident/recovery exercises without exposing secrets or pretending simulated results are live cloud data.

The product should prefer a modular monolith until measured team or scale constraints justify splitting a bounded worker. Provider services should be mapped and compared honestly; portability means explicit adapter seams and documented differences, not identical behavior across clouds.

**Success signals (proposed):** repeatable local setup; API/UI quality gates; documented end-to-end order flow; recovery drill evidence against approved RTO/RPO; no cloud resource without owner/budget/cleanup plan. **Not success:** number of technologies or files. Related: requirements specification and ADRs.
