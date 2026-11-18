#!/usr/bin/env bash
set -Eeuo pipefail

# Local/CI-only recovery exercise for the orders created by the Playwright E2E
# journey. It creates a new recovery_e2e_* database and never drops databases.

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

environment="${CLOUDRESILIENCE_ENVIRONMENT:-local}"
[[ "$environment" == "local" ]] || fail "only CLOUDRESILIENCE_ENVIRONMENT=local is allowed"

backup_id="${1:-}"
target_database="${2:-}"
backup_directory="${3:-}"
[[ "$backup_id" =~ ^[a-z0-9][a-z0-9_-]{0,50}$ ]] || fail "usage: $0 <backup-id> <recovery_e2e_database> <backup-directory>"
[[ "$target_database" =~ ^recovery_e2e_[a-z0-9_]{1,40}$ ]] || fail "target must match recovery_e2e_[a-z0-9_]+"
[[ -n "$backup_directory" ]] || fail "backup directory is required"

context="$(docker context show)" || fail "cannot read Docker context"
endpoint="$(docker context inspect "$context" --format '{{(index .Endpoints "docker").Host}}')" || fail "cannot inspect Docker endpoint"
case "$endpoint" in
  unix://*|npipe:////./pipe/*) ;;
  *) fail "refusing non-local Docker endpoint: $endpoint" ;;
esac

source_database="$(docker compose exec -T postgres printenv POSTGRES_DB | tr -d '\r\n')" || fail "cannot read Compose database"
database_user="$(docker compose exec -T postgres printenv POSTGRES_USER | tr -d '\r\n')" || fail "cannot read Compose database user"
[[ "$source_database" =~ ^cloudresilience(_[a-z0-9_]+)?$ ]] || fail "refusing non-local Compose database name: $source_database"
[[ -n "$database_user" ]] || fail "Compose database user is empty"

mkdir -p -- "$backup_directory"
backup_directory="$(cd "$backup_directory" && pwd -P)"
dump_path="$backup_directory/$backup_id.dump"
manifest_path="$backup_directory/$backup_id-recovery.json"
[[ ! -e "$dump_path" && ! -e "$manifest_path" ]] || fail "backup ID already exists; choose a new ID to preserve evidence"

container_dump="/tmp/${backup_id}.dump"
recovery_api="cloudresilience-api-recovery"
api_started=0
cleanup() {
  if [[ "$api_started" == 1 ]]; then docker stop "$recovery_api" >/dev/null 2>&1 || true; fi
  docker compose exec -T postgres rm -f "$container_dump" >/dev/null 2>&1 || true
  docker compose exec -T postgres rm -f "/tmp/${target_database}.dump" >/dev/null 2>&1 || true
}
trap cleanup EXIT

snapshot_sql=$(cat <<'SQL'
SELECT json_build_object(
  'flywayMigrations', (SELECT count(*) FROM flyway_schema_history WHERE success),
  'users', (SELECT count(*) FROM users),
  'products', (SELECT count(*) FROM products),
  'inventoryRows', (SELECT count(*) FROM inventory),
  'inventoryMovements', (SELECT count(*) FROM inventory_movements),
  'reservations', (SELECT count(*) FROM inventory_reservations),
  'orders', (SELECT count(*) FROM customer_orders),
  'orderItems', (SELECT count(*) FROM order_items),
  'payments', (SELECT count(*) FROM payments),
  'outboxEvents', (SELECT count(*) FROM outbox_events),
  'outboxUnsettled', (SELECT count(*) FROM outbox_events WHERE status IN ('PENDING','PROCESSING','FAILED')),
  'processedEvents', (SELECT count(*) FROM processed_events),
  'notifications', (SELECT count(*) FROM in_app_notifications),
  'orderActivities', (SELECT count(*) FROM order_activity),
  'deadLetters', (SELECT count(*) FROM dead_letter_messages),
  'ordersWithoutPayment', (
    SELECT count(*) FROM customer_orders o LEFT JOIN payments p ON p.order_id=o.id WHERE p.id IS NULL),
  'paymentOrderMismatches', (
    SELECT count(*) FROM customer_orders o JOIN payments p ON p.order_id=o.id
    WHERE p.status<>o.payment_status OR p.amount<>o.total_amount OR p.currency<>o.currency),
  'orderItemSubtotalMismatches', (
    SELECT count(*) FROM (
      SELECT o.id FROM customer_orders o LEFT JOIN order_items i ON i.order_id=o.id
      GROUP BY o.id,o.subtotal HAVING coalesce(sum(i.line_total),0)<>o.subtotal
    ) mismatches),
  'inventoryInvariantViolations', (
    SELECT count(*) FROM inventory WHERE quantity_on_hand<0 OR quantity_reserved<0 OR quantity_reserved>quantity_on_hand),
  'inventoryLedgerMismatches', (
    SELECT count(*) FROM inventory i
    LEFT JOIN LATERAL (
      SELECT m.new_quantity,m.new_reserved FROM inventory_movements m
      WHERE m.product_id=i.product_id
      ORDER BY m.created_at DESC,
        CASE m.movement_type WHEN 'ORDER_COMMIT' THEN 4 WHEN 'ORDER_RESERVATION_RELEASE' THEN 3
          WHEN 'ORDER_RESERVATION' THEN 2 ELSE 1 END DESC,
        m.id DESC LIMIT 1
    ) latest ON TRUE
    WHERE latest.new_quantity IS NULL OR latest.new_quantity<>i.quantity_on_hand OR latest.new_reserved<>i.quantity_reserved),
  'outboxAggregateMismatches', (
    SELECT count(*) FROM outbox_events e LEFT JOIN customer_orders o ON o.id=e.aggregate_id WHERE o.id IS NULL),
  'unprocessedEvents', (
    SELECT count(*) FROM outbox_events e
    WHERE NOT EXISTS (SELECT 1 FROM processed_events p WHERE p.event_id=e.id AND p.consumer_name='order-notifications')
       OR NOT EXISTS (SELECT 1 FROM processed_events p WHERE p.event_id=e.id AND p.consumer_name='order-activity'))
);
SQL
)

read_snapshot() {
  local database="$1"
  docker compose exec -T postgres psql --username="$database_user" --dbname="$database" \
    --set=ON_ERROR_STOP=1 --no-psqlrc --tuples-only --no-align --command="$snapshot_sql" \
    | tr -d '\r' | tail -n 1
}

# Wait for outbox publication and both durable inbox consumers. Poll current
# database state, with a firm bound; do not use a fixed arbitrary delay.
source_snapshot=''
drained=0
for _ in $(seq 1 120); do
  source_snapshot="$(read_snapshot "$source_database")" || fail "cannot read source recovery state"
  [[ "$(jq -r '.orders' <<<"$source_snapshot")" -gt 0 ]] || fail "E2E source contains no orders"
  [[ "$(jq -r '.payments' <<<"$source_snapshot")" -gt 0 ]] || fail "E2E source contains no payments"
  [[ "$(jq -r '.orderItems' <<<"$source_snapshot")" -gt 0 ]] || fail "E2E source contains no order items"
  [[ "$(jq -r '.outboxEvents' <<<"$source_snapshot")" -gt 0 ]] || fail "E2E source contains no outbox events"
  if [[ "$(jq -r '.outboxUnsettled' <<<"$source_snapshot")" == 0 && \
        "$(jq -r '.unprocessedEvents' <<<"$source_snapshot")" == 0 ]]; then
    drained=1
    break
  fi
  sleep 1
done
[[ "$drained" == 1 ]] || fail "outbox/inbox did not settle within 120 seconds"

for invariant in ordersWithoutPayment paymentOrderMismatches orderItemSubtotalMismatches \
  inventoryInvariantViolations inventoryLedgerMismatches outboxAggregateMismatches; do
  [[ "$(jq -r ".$invariant" <<<"$source_snapshot")" == 0 ]] || fail "source invariant failed: $invariant"
done

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
procedure_start="$(date +%s)"

database_exists="$(docker compose exec -T postgres psql --username="$database_user" --dbname=postgres \
  --set=ON_ERROR_STOP=1 --no-psqlrc --tuples-only --no-align \
  --command="SELECT 1 FROM pg_database WHERE datname='$target_database';" | tr -d '\r\n')"
[[ -z "$database_exists" ]] || fail "target database already exists; refusing to overwrite it"

docker compose exec -T postgres pg_dump --format=custom --no-owner --no-acl \
  --file="$container_dump" --username="$database_user" --dbname="$source_database"
docker compose cp "postgres:$container_dump" "$dump_path"
docker compose exec -T postgres rm -f "$container_dump" >/dev/null
checksum="$(sha256sum "$dump_path" | awk '{print $1}')"
bytes="$(wc -c <"$dump_path" | tr -d ' ' )"
docker compose cp "$dump_path" "postgres:/tmp/${target_database}.dump"
docker compose exec -T postgres pg_restore --username="$database_user" --list "/tmp/${target_database}.dump" >/dev/null
docker compose exec -T postgres createdb --username="$database_user" --maintenance-db=postgres \
  --owner="$database_user" "$target_database"
docker compose exec -T postgres pg_restore --username="$database_user" --exit-on-error --no-owner --no-acl \
  --dbname="$target_database" "/tmp/${target_database}.dump"
docker compose exec -T postgres rm -f "/tmp/${target_database}.dump" >/dev/null

restored_snapshot="$(read_snapshot "$target_database")" || fail "cannot read restored recovery state"
for invariant in ordersWithoutPayment paymentOrderMismatches orderItemSubtotalMismatches \
  inventoryInvariantViolations inventoryLedgerMismatches outboxAggregateMismatches; do
  [[ "$(jq -r ".$invariant" <<<"$restored_snapshot")" == 0 ]] || fail "restored invariant failed: $invariant"
done
[[ "$(jq -S . <<<"$source_snapshot")" == "$(jq -S . <<<"$restored_snapshot")" ]] || fail "source and restored database snapshots differ"

# Start a temporary API against the isolated restored DB. It is removed by the
# EXIT trap. Existing Compose services and volumes are left untouched.
docker compose run --detach --no-deps --rm --name "$recovery_api" \
  --publish 127.0.0.1:18080:8080 --publish 127.0.0.1:18081:8081 \
  --env "SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${target_database}?connectTimeout=5&socketTimeout=10" api >/dev/null
api_started=1
ready=0
for _ in $(seq 1 60); do
  if curl --fail --silent http://127.0.0.1:18081/actuator/health/readiness \
      | jq -e '.status == "UP"' >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
[[ "$ready" == 1 ]] || fail "recovery API did not become ready"
catalog_response="$(curl --fail --silent http://127.0.0.1:18080/api/v1/products)" || fail "recovered catalog endpoint failed"
jq -e '.totalElements > 0 and (.content | length > 0)' <<<"$catalog_response" >/dev/null || fail "recovered catalog response is empty"

finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
procedure_seconds="$(( $(date +%s) - procedure_start ))"
jq -n \
  --arg backupId "$backup_id" \
  --arg sourceDatabase "$source_database" \
  --arg targetDatabase "$target_database" \
  --arg createdAtUtc "$started_at" \
  --arg completedAtUtc "$finished_at" \
  --arg sha256 "$checksum" \
  --argjson bytes "$bytes" \
  --argjson procedureDurationSeconds "$procedure_seconds" \
  --argjson source "$source_snapshot" \
  --argjson restored "$restored_snapshot" \
  --arg apiReadiness "PASS" \
  --arg catalogSmoke "PASS" \
  '{exerciseId:$backupId,environment:"local-compose",sourceDatabase:$sourceDatabase,targetDatabase:$targetDatabase,
    startedAtUtc:$createdAtUtc,completedAtUtc:$completedAtUtc,backup:{format:"pg_dump custom",bytes:$bytes,sha256:$sha256},
    source:$source,restored:$restored,apiReadiness:$apiReadiness,catalogSmoke:$catalogSmoke,
    snapshotsMatch:true,procedureDurationSeconds:$procedureDurationSeconds,actualRtoSeconds:null,actualRpo:null}' \
  > "$manifest_path"

printf 'PASS: order/payment PostgreSQL recovery; report=%s\n' "$manifest_path"
cat "$manifest_path"
