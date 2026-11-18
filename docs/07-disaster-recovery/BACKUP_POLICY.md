# Backup policy

**Status:** local backup/restore scripts are implemented and PowerShell parser-validated; no Docker backup or restore run has been performed. Never treat a Docker named volume as a backup.

## PostgreSQL

- PostgreSQL is the sole durable authority for business state. `infrastructure/scripts/Backup-Postgres.ps1` creates a PostgreSQL custom archive using `pg_dump`, then emits JSON metadata with backup ID, UTC time, database, server version, source environment, file size and SHA-256 checksum. Schema/Flyway version and tool version are not currently included in that metadata.
- Use `infrastructure/scripts/Get-Recovery-Status.ps1 -TargetDatabase recovery_<id>` after a restore to report Flyway, product, order, payment, inventory, outbox and dead-letter counts plus inventory-invariant and missing-payment counts. It is read-only. Its queries have syntax validation only and have not yet run against PostgreSQL in this environment.
- Store production backup artifacts encrypted, access-controlled and separate from the source failure domain. Do not put credentials, `.env` files, session cookies or secret values in the archive or metadata.
- Retention target: seven daily restore points for the non-production reference environment. Business owners must approve production retention, legal hold and deletion controls before deployment.
- Phase 8 Terraform configures RDS automated backups with seven-day retention and PITR capability through RDS. This is source configuration only, not evidence that backups exist. Confirm AWS retention/PITR settings and test restore after approved provisioning.
- Periodically restore into a new isolated database, verify checksum before restore, run Flyway validation and data invariants, then record measured results.

Example PowerShell invocation (syntax-validated, not executed because Docker is unavailable locally):

```powershell
./infrastructure/scripts/Backup-Postgres.ps1 -BackupId <unique-id> -BackupDirectory <controlled-directory>
```

The script runs `pg_dump --format=custom` inside the local Compose PostgreSQL container, copies the archive to the selected directory, computes SHA-256 and writes a JSON sidecar. It refuses to overwrite an existing backup ID and supports only `-Environment local`. Parser syntax passed; backup execution and artifact verification have not been run because Docker is unavailable. Protect files and host storage; local Compose defaults are development-only.

## Other components

- **RabbitMQ:** durable queues/messages and a persistent local volume are configured, but volume persistence is not a broker backup. Capture topology and use a broker-supported export/backup procedure after selecting a production broker; reconcile deliveries with PostgreSQL after recovery.
- **Redis:** session data is ephemeral. Do not back it up as business data; empty-store recovery causes session re-authentication. Local Redis AOF is for container restart behavior only.
- **Images/configuration:** retain immutable container images and reviewed source commit SHAs. Keep Terraform state encrypted and access-restricted; state is not a data backup.
- **Observability:** define separate retention based on incident evidence needs. Redact secrets/PII and restrict access.
- **Secrets and keys:** never archive plaintext secrets. Ensure authorized key access and secret rotation runbooks are available to the recovery team.

## Verification and ownership

Before relying on a backup, verify provenance, successful completion, nonzero plausible size, checksum, encryption, retention, access permissions and restore compatibility. Assign backup operator and independent verifier. Record evidence without customer data or credentials. The Phase 9 report records the current drill as NOT RUN.
