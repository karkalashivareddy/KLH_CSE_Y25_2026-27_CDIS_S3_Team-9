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

$sql = @'
BEGIN;
INSERT INTO users(id,email,password_hash,first_name,last_name,enabled,account_locked)
VALUES ('60000000-0000-4000-8000-000000000001','e2e-fixture@example.invalid','disabled-fixture-hash','E2E','Fixture',false,true)
ON CONFLICT (id) DO NOTHING;
INSERT INTO categories(id,name,slug)
VALUES ('60000000-0000-4000-8000-000000000002','E2E Fixtures','e2e-fixtures')
ON CONFLICT (id) DO NOTHING;
INSERT INTO products(id,sku,name,slug,short_description,price,category_id,status)
VALUES ('60000000-0000-4000-8000-000000000003','E2E-FIXTURE-001','E2E Fixture Product','e2e-fixture-product','Synthetic product for local browser verification',9.99,'60000000-0000-4000-8000-000000000002','ACTIVE')
ON CONFLICT (id) DO NOTHING;
INSERT INTO inventory(id,product_id,quantity_on_hand,quantity_reserved,version)
VALUES ('60000000-0000-4000-8000-000000000004','60000000-0000-4000-8000-000000000003',5,0,1)
ON CONFLICT (product_id) DO NOTHING;
WITH current_stock AS (
    SELECT product_id, quantity_on_hand, quantity_reserved
    FROM inventory
    WHERE product_id = '60000000-0000-4000-8000-000000000003'
    FOR UPDATE
), topped_up AS (
    UPDATE inventory i
    SET quantity_on_hand = GREATEST(i.quantity_on_hand, i.quantity_reserved + 5),
        version = i.version + 1,
        updated_at = CURRENT_TIMESTAMP
    FROM current_stock c
    WHERE i.product_id = c.product_id
      AND i.quantity_on_hand - i.quantity_reserved < 5
    RETURNING i.product_id, c.quantity_on_hand AS previous_quantity,
              i.quantity_on_hand AS new_quantity, c.quantity_reserved
)
INSERT INTO inventory_movements(
    id,product_id,movement_type,quantity_delta,reserved_delta,
    previous_quantity,new_quantity,previous_reserved,new_reserved,reason,performed_by
)
SELECT gen_random_uuid(), product_id, 'STOCK_IN', new_quantity - previous_quantity, 0,
       previous_quantity, new_quantity, quantity_reserved, quantity_reserved,
       'Restore minimum stock for local E2E fixture',
       '60000000-0000-4000-8000-000000000001'
FROM topped_up;
COMMIT;
SELECT json_build_object('sku', p.sku, 'quantityOnHand', i.quantity_on_hand,
                         'quantityReserved', i.quantity_reserved,
                         'available', i.quantity_on_hand - i.quantity_reserved)
FROM products p JOIN inventory i ON i.product_id = p.id
WHERE p.id = '60000000-0000-4000-8000-000000000003';
'@

$result = docker compose exec -T postgres psql --username=$databaseUser --dbname=$database --set=ON_ERROR_STOP=1 --no-psqlrc --tuples-only --no-align --command=$sql
if ($LASTEXITCODE -ne 0) { throw 'Seeding the local E2E catalog fixture failed.' }
$result | Where-Object { $_.Trim().StartsWith('{') } | Select-Object -Last 1
