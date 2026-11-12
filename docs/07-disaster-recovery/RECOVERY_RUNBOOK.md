# Recovery exercise runbook (design)

**Purpose/scope:** safe template for a future isolated recovery exercise. This is not authorization to run recovery and has not been executed. Related: DR-001–004, ADR-008, disaster recovery architecture, security baseline.

## Entry criteria and roles

Use a disposable isolated environment with approved owner, budget, data classification, backup point, and cleanup window. Name incident lead, recovery operator, independent approver, data verifier, and observer. Verify MFA, scoped permissions, contact list, and rollback owner. Never target a production account without explicit written authorization.

## Procedure

1. Record exercise ID, scenario, environment, start time, team, target RTO/RPO and chosen recovery point.
2. Confirm isolation, access approval, immutable backup and encryption-key availability; check resource quota/cost ceiling.
3. Record the simulated failure and data cutoff; stop unsafe writes if applicable.
4. Restore infrastructure and data to isolated targets; do not overwrite source data.
5. Verify backup integrity, schema/version compatibility, row counts/checksums and security controls.
6. Run health, authorization, order consistency and synthetic-payment smoke tests.
7. Approver decides controlled cutover or rollback. In an exercise, do not alter public traffic.
8. Record observed recovery duration/data gap, errors, evidence location, decisions and limitations.
9. Securely delete temporary resources/data, confirm backup retention, and review resulting cost.
10. Hold a blameless review; assign remediation owners and retest date.

## Stop conditions and evidence

Stop for unexpected public exposure, missing approval, unclear backup provenance, key/access anomaly, budget ceiling, data integrity mismatch, or unintended source mutation. Preserve audit evidence without secrets/PII. Record wall-clock RTO/RPO from scenario timestamps, not estimates. No success may be claimed until the restore and security checks pass. Validation: tabletop review before first exercise; timed isolated restore before accepting DR targets.
