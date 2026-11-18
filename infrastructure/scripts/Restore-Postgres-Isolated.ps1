[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $BackupPath,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^recovery_[a-z0-9_]{1,48}$')]
    [string] $TargetDatabase,

    [Parameter(Mandatory = $true)]
    [switch] $ConfirmRestore,

    [ValidateSet('local')]
    [string] $Environment = 'local'
)

$ErrorActionPreference = 'Stop'
if ($Environment -ne 'local') { throw 'Only local isolated restore targets are supported.' }
if (-not $ConfirmRestore) { throw 'Pass -ConfirmRestore after verifying the target is an isolated local database.' }
$resolvedBackup = (Resolve-Path -LiteralPath $BackupPath).Path
$metadataPath = [System.IO.Path]::ChangeExtension($resolvedBackup, '.json')
if (-not (Test-Path -LiteralPath $metadataPath)) { throw 'Backup metadata file is missing.' }
$metadata = Get-Content -LiteralPath $metadataPath -Raw | ConvertFrom-Json
$actualHash = (Get-FileHash -LiteralPath $resolvedBackup -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualHash -ne $metadata.sha256) { throw 'Backup checksum does not match its metadata.' }

$containerPath = "/tmp/$TargetDatabase.dump"
docker compose cp $resolvedBackup "postgres:$containerPath"
if ($LASTEXITCODE -ne 0) { throw 'Could not copy the backup into the PostgreSQL container.' }
try {
    docker compose exec -T postgres pg_restore --list $containerPath | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore could not parse the backup archive.' }
    $databaseOwner = if ([string]::IsNullOrWhiteSpace($env:POSTGRES_USER)) { 'cloudresilience' } else { $env:POSTGRES_USER }
    docker compose exec -T postgres createdb --maintenance-db=postgres --owner=$databaseOwner $TargetDatabase
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated restore database; it may already exist.' }
    docker compose exec -T postgres pg_restore --exit-on-error --no-owner --no-acl --dbname=$TargetDatabase $containerPath
    if ($LASTEXITCODE -ne 0) { throw 'pg_restore failed; inspect the isolated database before retrying.' }
    $validation = docker compose exec -T postgres psql --no-psqlrc --tuples-only --no-align --dbname=$TargetDatabase --command="SELECT current_database(), count(*) FROM information_schema.tables WHERE table_schema = 'public';"
    if ($LASTEXITCODE -ne 0) { throw 'Post-restore schema validation query failed.' }
    [pscustomobject]@{
        targetDatabase = $TargetDatabase
        backupId = $metadata.backupId
        checksumVerified = $true
        schemaValidation = ($validation -join '').Trim()
        restoredAtUtc = [DateTime]::UtcNow.ToString('o')
    }
}
finally {
    docker compose exec -T postgres rm -f $containerPath | Out-Null
}
