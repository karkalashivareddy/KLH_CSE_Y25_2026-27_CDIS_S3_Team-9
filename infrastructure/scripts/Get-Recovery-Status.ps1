[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^recovery_[a-z0-9_]{1,48}$')]
    [string] $TargetDatabase,

    [ValidateSet('local')]
    [string] $Environment = 'local'
)

$ErrorActionPreference = 'Stop'
if ($Environment -ne 'local') { throw 'Only local recovery databases may be inspected by this script.' }
$query = @'
SELECT json_build_object(
  'database', current_database(),
  'flywayMigrations', (SELECT count(*) FROM flyway_schema_history WHERE success),
  'products', (SELECT count(*) FROM products),
  'orders', (SELECT count(*) FROM customer_orders),
  'payments', (SELECT count(*) FROM payments),
  'inventoryRows', (SELECT count(*) FROM inventory),
  'reservedRows', (SELECT count(*) FROM inventory WHERE quantity_reserved > 0),
  'pendingOutbox', (SELECT count(*) FROM outbox_events WHERE status IN ('PENDING','PROCESSING')),
  'deadLetters', (SELECT count(*) FROM dead_letter_messages WHERE status = 'QUARANTINED'),
  'inventoryInvariantViolations', (SELECT count(*) FROM inventory WHERE quantity_on_hand < 0 OR quantity_reserved < 0 OR quantity_reserved > quantity_on_hand),
  'ordersWithoutPayment', (SELECT count(*) FROM customer_orders o LEFT JOIN payments p ON p.order_id = o.id WHERE p.id IS NULL)
);
'@
$result = docker compose exec -T postgres psql --no-psqlrc --tuples-only --no-align --dbname=$TargetDatabase --command=$query
if ($LASTEXITCODE -ne 0) { throw 'Recovery status query failed; confirm migrations and target database.' }
$jsonLine = ($result | Where-Object { $_.Trim().StartsWith('{') } | Select-Object -Last 1).Trim()
if (-not $jsonLine) { throw 'PostgreSQL returned no recovery status document.' }
$status = $jsonLine | ConvertFrom-Json
$status | ConvertTo-Json -Depth 4
