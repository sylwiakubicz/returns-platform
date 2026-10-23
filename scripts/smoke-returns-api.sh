#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
base_url="${1:-http://localhost:8080}"
base_url="${base_url%/}"
for command in curl jq cmp; do
  command -v "$command" >/dev/null || { echo "Missing command: $command" >&2; exit 1; }
done
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/returns-api.XXXXXX")"
trap 'rm -rf "$work_dir"' EXIT
run_key="api-smoke-$(date -u +%Y%m%dT%H%M%S)-$$-${RANDOM}"

# Aktualizujemy datę tylko w kopii, aby przykład nie wygasł po dwóch tygodniach.
jq '.orderDeliveredAt = ((now - 86400) | strftime("%Y-%m-%dT%H:%M:%SZ"))' \
  "$repo_dir/examples/create-return.json" > "$work_dir/request.json"

request() {
  local method="$1" path="$2" expected="$3" body_file="${4:-}" key="${5:-}"
  local args=(--silent --show-error --connect-timeout 5 --max-time 30
    --request "$method" --header 'Accept: application/json'
    --output "$work_dir/response.json" --dump-header "$work_dir/headers"
    --write-out '%{http_code}')
  if [[ -n "$body_file" ]]; then
    args+=(--header 'Content-Type: application/json' --data-binary "@$body_file")
  fi
  if [[ -n "$key" ]]; then args+=(--header "Idempotency-Key: $key"); fi
  local actual
  actual="$(curl "${args[@]}" "$base_url$path")"
  if [[ "$actual" != "$expected" ]]; then
    echo "$method $path: expected $expected, received $actual" >&2
    cat "$work_dir/response.json" >&2
    exit 1
  fi
}

assert_json() { jq -e "$1" "$work_dir/response.json" >/dev/null; }
assert_same_json() {
  jq -S . "$1" > "$work_dir/expected.json"
  jq -S . "$work_dir/response.json" > "$work_dir/actual.json"
  cmp -s "$work_dir/expected.json" "$work_dir/actual.json" || {
    echo 'Replayed response differs from the original' >&2; exit 1;
  }
}
simulate() {
  request POST "/internal/sim/returns/$number/$1" 204 "${2:-}"
}

request POST /api/v1/returns 201 "$work_dir/request.json" "$run_key"
assert_json '.status == "REQUESTED" and (.businessNumber | test("^RMA-[0-9]{4}-[0-9]{6}$"))'
number="$(jq -r .businessNumber "$work_dir/response.json")"
location="$(awk 'tolower($1) == "location:" {sub(/\r$/, "", $2); print $2}' "$work_dir/headers")"
[[ "$location" == "/api/v1/returns/$number" ]] || { echo 'Invalid Location header' >&2; exit 1; }
cp "$work_dir/response.json" "$work_dir/created.json"
request POST /api/v1/returns 201 "$work_dir/request.json" "$run_key"
assert_same_json "$work_dir/created.json"
jq '.orderId += "-different"' "$work_dir/request.json" > "$work_dir/changed.json"
request POST /api/v1/returns 422 "$work_dir/changed.json" "$run_key"
assert_json '.code == "IDEMPOTENCY_KEY_REUSED"'

request GET "$location" 200
assert_json '.status == "REQUESTED" and .refundAmount == null and (.updatedAt | type == "string")'
request GET '/api/v1/returns?page=0&size=20&status=REQUESTED' 200
assert_json '.page == 0 and .size == 20 and all(.content[]; .status == "REQUESTED")'

request GET '/api/v1/returns?size=101' 400
assert_json '.code == "INVALID_REQUEST" and (.errors | length > 0)'
# Numer zarezerwowany poza sekwencją generatora (zaczyna się od 000001).
request GET '/api/v1/returns/RMA-2026-000000' 404
assert_json '.code == "RETURN_NOT_FOUND"'
jq -n '{warehouseId:"warehouse-example"}' > "$work_dir/warehouse.json"
request POST "/internal/sim/returns/$number/parcel-received" 409 "$work_dir/warehouse.json"
assert_json '.code == "INVALID_STATE_TRANSITION"'

jq -n --arg id "$run_key" '{labelId:$id, trackingNumber:$id, labelFileKey:("labels/"+$id+".pdf")}' > "$work_dir/label.json"
simulate label-issued "$work_dir/label.json"
jq -n --arg id "$run_key" '{pickupId:$id, scheduledFor:((now + 86400) | strftime("%Y-%m-%d"))}' > "$work_dir/pickup.json"
simulate pickup-scheduled "$work_dir/pickup.json"
simulate parcel-in-transit
jq -n '{warehouseId:"warehouse-example"}' > "$work_dir/warehouse.json"
simulate parcel-received "$work_dir/warehouse.json"
jq -n '{inspectorId:"inspector-example"}' > "$work_dir/inspector.json"
simulate inspection-started "$work_dir/inspector.json"
jq '{inspectorId:"inspector-example", results:[.items[] | {itemId:.itemId, grade:"A", photoKeys:[]}]}' \
  "$work_dir/request.json" > "$work_dir/inspection.json"
simulate inspection-completed "$work_dir/inspection.json"
request GET "$location" 200
assert_json '.status == "REFUND_PENDING" and (.refundAmount.amount | type == "string")'
jq --arg id "$run_key" '{payoutId:$id, amount:.refundAmount, attempt:1}' \
  "$work_dir/response.json" > "$work_dir/refund.json"
simulate refund-executed "$work_dir/refund.json"
jq -n '{closureReason:"REFUND_COMPLETED"}' > "$work_dir/close.json"
simulate close "$work_dir/close.json"
request GET "$location" 200
assert_json '.status == "CLOSED"'
request GET "$location/history" 200
assert_json 'length == 10 and [.[].version] == [range(1;11)] and [.[].type] == ["RmaRequested","LabelIssued","PickupScheduled","ParcelInTransit","ParcelReceived","InspectionStarted","InspectionCompleted","RefundDecided","RefundExecuted","RmaClosed"]'
# Nawet po zamknięciu ponowienie tworzenia odtwarza pierwotną odpowiedź REQUESTED.
request POST /api/v1/returns 201 "$work_dir/request.json" "$run_key"
assert_same_json "$work_dir/created.json"

# Osobny zwrot pokazuje anulowanie; nie zmieniamy zakończonego procesu powyżej.
request POST /api/v1/returns 201 "$work_dir/request.json" "$run_key-cancel"
cancel_number="$(jq -r .businessNumber "$work_dir/response.json")"
jq -n '{cancelledBy:"customer-example-1", reason:"API smoke test"}' > "$work_dir/cancel.json"
request POST "/api/v1/returns/$cancel_number/cancel" 204 "$work_dir/cancel.json"
request POST "/api/v1/returns/$cancel_number/cancel" 409 "$work_dir/cancel.json"
assert_json '.code == "CANCELLATION_NOT_ALLOWED"'
request GET "/api/v1/returns/$cancel_number" 200
assert_json '.status == "CANCELLED"'
printf 'API smoke passed: %s CLOSED, %s CANCELLED\n' "$number" "$cancel_number"
