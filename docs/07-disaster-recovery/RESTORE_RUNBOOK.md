# Isolated PostgreSQL restore runbook

**Status:** procedural runbook; no restore drill has been executed. These commands are examples for a disposable local environment, not production instructions. Never target production or overwrite a source database.

The implemented helper is `infrastructure/scripts/Restore-Postgres-Isolated.ps1`. Its PowerShell syntax passed parser validation; Docker behavior has not been executed. It accepts `-BackupPath`, a target database matching `recovery_<lowercase-name>`, and mandatory `-ConfirmRestore`; it supports only `-Environment local`. It verifies the JSON sidecar checksum, parses the archive, creates a new database in the local Compose PostgreSQL service, restores there, and performs a basic schema-table-count query. It does not itself validate Flyway or business invariants, so complete the checks below before treating a restore as successful.

## Preconditions and stop conditions

- Obtain environment-owner approval and a disposable isolated target. Identify backup ID, source commit, target schema version, cutoff time and expected RPO.
- Verify host and database names resolve to the isolated target. Preserve source data unchanged. Confirm sufficient disk, backup checksum, PostgreSQL compatibility and access to required keys.
- Stop if target identity is unclear, checksum fails, credentials would be exposed in logs, source could be modified, or order/inventory/payment consistency cannot be explained.

## Restore procedure

1. Record exercise ID and UTC start time; capture backup checksum and metadata.
2. Start a fresh isolated PostgreSQL service/database using the project's pinned PostgreSQL major version. Do not attach or reuse the source volume.
3. Verify checksum before using the archive:

   ```sh
   sha256sum -c backups/<backup-file>.sha256
   ```

4. Restore the custom archive into a newly created empty database. Provide credentials through a protected local `.pgpass` or interactive prompt, not command-line arguments:

   ```sh
   createdb -h <isolated-host> -U <restore-user> cloudresilience_restore
   pg_restore --exit-on-error --no-owner --no-privileges -h <isolated-host> -U <restore-user> -d cloudresilience_restore backups/<backup-file>.dump
   ```

5. Check schema and migration history. Use the repository's Flyway validation/startup behavior against the isolated database; do not run migrations on the source.
6. Verify identity/catalog rows and constraints; compare expected counts where metadata provides a safe baseline.
7. Validate stock quantities against inventory ledger and active reservations; confirm reservation/order status and payment/order state consistency.
8. Inspect `outbox_events` and `processed_events` if present. Record backlog and event age. Do not purge inbox rows or replay events until consumer idempotency and replay procedure are verified.
9. Keep event workers stopped while checking broker state. Restore/recreate the RabbitMQ topology and compare available messages with outbox status. Resume publishing/consuming only under the approved reconciliation plan.
10. Start the API against the isolated database with appropriate dependencies. Test readiness, login, catalog, cart, order history and admin order workflow with synthetic test data.
11. Record verification completion, timestamps, observed recovery time, data-loss window, exceptions and reviewer. Clean up only the isolated environment under its owner’s approved procedure.

## Reconciliation checklist

- Flyway schema/history: consistent with the tested application commit.
- Inventory: no negative available stock; ledger movement and reservation totals reconcile.
- Orders/reservations: terminal and active states agree with reservation records.
- Payments: simulated payment state agrees with order state; no duplicate external payment effects exist.
- Outbox/inbox: committed events retained, duplicate delivery safe, no blind replay.
- Broker: topology durable; redelivery/DLQ handled under the documented event policy.
- Redis: may be empty; customers can sign in again, and PostgreSQL cart/order data remains.

Any failed check blocks cutover. A successful local restore does not establish cloud or regional recovery capability.
