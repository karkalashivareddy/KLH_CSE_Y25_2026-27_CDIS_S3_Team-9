[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')]
    [string] $BackupId,

    [Parameter(Mandatory = $true)]
    [string] $BackupDirectory,

    [ValidatePattern('^drtest_[a-z0-9_]{1,40}$')]
    [string] $SourceDatabase,

    [ValidateSet('local')]
    [string] $Environment = 'local'
)

$ErrorActionPreference = 'Stop'
if ($Environment -ne 'local') { throw 'Only the local Compose environment is supported by this script.' }
$resolvedDirectory = [System.IO.Path]::GetFullPath($BackupDirectory)
New-Item -ItemType Directory -Path $resolvedDirectory -Force | Out-Null
$dumpPath = Join-Path $resolvedDirectory "$BackupId.dump"
$metadataPath = Join-Path $resolvedDirectory "$BackupId.json"
if ((Test-Path -LiteralPath $dumpPath) -or (Test-Path -LiteralPath $metadataPath)) {
    throw 'Backup ID already exists; choose a new ID to preserve prior evidence.'
}

$configuredDatabase = (docker compose exec -T postgres printenv POSTGRES_DB).Trim()
if ($LASTEXITCODE -ne 0 -or -not $configuredDatabase) { throw 'Could not read the configured Compose database name.' }
$database = if ($SourceDatabase) { $SourceDatabase } else { $configuredDatabase }
$databaseUser = (docker compose exec -T postgres printenv POSTGRES_USER).Trim()
if ($LASTEXITCODE -ne 0 -or -not $databaseUser) { throw 'Could not read the configured Compose database user.' }
docker compose exec -T postgres pg_dump --format=custom --no-owner --no-acl --file="/tmp/$BackupId.dump" --username=$databaseUser --dbname=$database
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed; no verified backup was created.' }
try {
    docker compose cp "postgres:/tmp/$BackupId.dump" $dumpPath
    if ($LASTEXITCODE -ne 0) { throw 'Copying the database dump from the container failed.' }
}
finally {
    docker compose exec -T postgres rm -f "/tmp/$BackupId.dump" | Out-Null
}

$sha256 = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash.ToLowerInvariant()
$serverInfo = docker compose exec -T postgres psql --username=$databaseUser --dbname=$database --no-psqlrc --tuples-only --no-align --command='SHOW server_version;'
if ($LASTEXITCODE -ne 0) { throw 'Could not record PostgreSQL server version.' }
$metadata = [ordered]@{
    backupId = $BackupId
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
    environment = $Environment
    database = $database
    postgresVersion = (($serverInfo -join '').Trim())
    format = 'pg_dump custom'
    file = [System.IO.Path]::GetFileName($dumpPath)
    bytes = (Get-Item -LiteralPath $dumpPath).Length
    sha256 = $sha256
}
[System.IO.File]::WriteAllText($metadataPath, ($metadata | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))
[pscustomobject]$metadata
