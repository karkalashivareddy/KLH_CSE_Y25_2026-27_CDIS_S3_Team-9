# ADR-038: PostgreSQL backup and isolated restore

- **Status:** Accepted as a design direction; implementation and drill pending
- **Date:** 2026-11-18

## Context

PostgreSQL stores identity, catalog, inventory, cart, orders, payments, reservations, outbox and inbox. A persistent Docker volume is not a recoverable backup. Cloud target Terraform defines RDS automated backups, but no cloud system is provisioned and no restore evidence exists.

## Decision

Use consistent PostgreSQL logical backups for local exercises with metadata and SHA-256 verification. For an approved AWS deployment, use encrypted RDS automated backups/PITR plus an explicitly approved retention and alternate-region strategy. Restore into a new isolated target, verify Flyway/schema and business invariants, and require an independent approval before any cutover. Never overwrite production as part of an exercise.

## Consequences

Backup correctness is not accepted until an isolated restore passes. Restore duration and data-loss window remain unknown until measured. Backup artifacts and keys require separate access controls and retention. Redis session state is not part of business backup.

## Validation

Local backup, checksum and restore: NOT RUN. Cloud restore/PITR: NOT RUN. No cloud resources exist.
