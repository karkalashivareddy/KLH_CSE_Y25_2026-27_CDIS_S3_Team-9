[CmdletBinding()]
param(
    [ValidateSet('local')]
    [string] $Environment = 'local'
)

$ErrorActionPreference = 'Stop'
if ($Environment -ne 'local') { throw 'E2E fixtures may only be seeded into local Compose.' }
$databaseOutput = docker compose exec -T postgres printenv POSTGRES_DB
if ($LASTEXITCODE -ne 0) { throw 'Could not read the configured Compose database name.' }
$database = ($databaseOutput -join '').Trim()
if (-not $database -or $database -notmatch '^cloudresilience(?:_[a-z0-9_]+)?$') {
    throw 'Refusing to seed: the Compose database name is not an approved local CloudResilience database.'
}
$databaseUserOutput = docker compose exec -T postgres printenv POSTGRES_USER
if ($LASTEXITCODE -ne 0) { throw 'Could not read the configured Compose database user.' }
$databaseUser = ($databaseUserOutput -join '').Trim()
if (-not $databaseUser) { throw 'Could not read the configured Compose database user.' }

$fixturePath = Join-Path $PSScriptRoot '..\..\apps\web\e2e\fixtures\seed-catalog.sql'
$sql = Get-Content -LiteralPath (Resolve-Path $fixturePath) -Raw

$result = docker compose exec -T postgres psql --username=$databaseUser --dbname=$database --set=ON_ERROR_STOP=1 --no-psqlrc --tuples-only --no-align --command=$sql
if ($LASTEXITCODE -ne 0) { throw 'Seeding the local E2E catalog fixture failed.' }
$result | Where-Object { $_.Trim().StartsWith('{') } | Select-Object -Last 1
