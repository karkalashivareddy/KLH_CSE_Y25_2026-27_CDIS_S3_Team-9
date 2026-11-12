# ADR-008: Recovery uses tested backup restore and controlled cutover

- **Status:** Proposed; objectives/approval pending
- **Date:** 2026-11-12
- **Context/problem:** recovery claims need measured restore evidence; active-active multi-region raises replication, conflict and cost complexity.
- **Options:** backup/restore; pilot light; warm standby; active-active multi-region.
- **Decision:** design first for encrypted backups, infrastructure recreation, isolated restore validation, documented decision gates and controlled cutover. Proposed RTO/RPO are 4 h/15 min, unapproved.
- **Justification/advantages:** lower initial complexity/cost, validates backup integrity and runbooks before higher availability investment.
- **Trade-offs/consequences:** recovery time may exceed objective; replication point determines data loss; region/account dependencies require exercises.
- **Reconsider when:** approved workload/business impact or measured restore time cannot meet objectives.
- **Validation/dependencies:** timed restore drill with integrity check, approver, observed RTO/RPO and cleanup; none executed.
