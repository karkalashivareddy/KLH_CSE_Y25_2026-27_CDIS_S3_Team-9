[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$')]
    [string] $BackupId,

    [Parameter(Mandatory = $true)]
    [string] $BackupDirectory,

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

docker compose exec -T postgres pg_dump --format=custom --no-owner --no-acl --file="/tmp/$BackupId.dump"
if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed; no verified backup was created.' }
try {
    docker compose cp "postgres:/tmp/$BackupId.dump" $dumpPath
    if ($LASTEXITCODE -ne 0) { throw 'Copying the database dump from the container failed.' }
}
finally {
    docker compose exec -T postgres rm -f "/tmp/$BackupId.dump" | Out-Null
}

$sha256 = (Get-FileHash -LiteralPath $dumpPath -Algorithm SHA256).Hash.ToLowerInvariant()
$serverInfo = docker compose exec -T postgres psql --no-psqlrc --tuples-only --no-align --command="SELECT current_database() || '|' || current_setting('server_version');"
if ($LASTEXITCODE -ne 0) { throw 'Could not record PostgreSQL server version.' }
$serverFields = (($serverInfo -join '').Trim() -split '\|', 2)
$metadata = [ordered]@{
    backupId = $BackupId
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
    environment = $Environment
    database = $serverFields[0]
    postgresVersion = $serverFields[1]
    format = 'pg_dump custom'
    file = [System.IO.Path]::GetFileName($dumpPath)
    bytes = (Get-Item -LiteralPath $dumpPath).Length
    sha256 = $sha256
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding utf8NoBOM
[pscustomobject]$metadata
