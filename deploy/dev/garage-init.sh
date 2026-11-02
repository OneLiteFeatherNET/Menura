#!/bin/sh
# One-shot bootstrap of the local Garage node, run by the garage-init service
# of docker-compose.yml. It assigns the single-node layout, creates the private
# development bucket and imports the development S3 key with full access to it.
# Re-running it changes nothing. It talks to the Garage admin API only.
set -eu

ADMIN_URL="${GARAGE_ADMIN_URL:-http://garage:3903}"
ADMIN_TOKEN="${GARAGE_ADMIN_TOKEN:?GARAGE_ADMIN_TOKEN must be set}"
BUCKET="${GARAGE_BUCKET:-menura-dev}"
KEY_ID="${GARAGE_ACCESS_KEY_ID:?GARAGE_ACCESS_KEY_ID must be set}"
KEY_SECRET="${GARAGE_SECRET_ACCESS_KEY:?GARAGE_SECRET_ACCESS_KEY must be set}"
ZONE="dc1"
CAPACITY=1073741824 # 1 GiB, development only

# api METHOD PATH [JSON_BODY] prints the response as compact JSON (no spaces or
# newlines), so the grep patterns below can rely on `"key":value` spelling.
api() {
  method="$1"
  path="$2"
  body="${3:-}"
  # The `|| return 1` keeps curl's failure visible to callers: a status of the
  # pipe below would be tr's, and `if ! api ...` checks would never see a 404.
  if [ -n "$body" ]; then
    response="$(curl -fsS -X "$method" -H "Authorization: Bearer $ADMIN_TOKEN" \
      -H "Content-Type: application/json" -d "$body" "$ADMIN_URL$path")" || return 1
  else
    response="$(curl -fsS -X "$method" -H "Authorization: Bearer $ADMIN_TOKEN" \
      "$ADMIN_URL$path")" || return 1
  fi
  printf '%s' "$response" | tr -d ' \n\r\t'
}

first_id() {
  grep -o '"id":"[0-9a-f]*"' | head -n 1 | cut -d'"' -f4
}

# wait_for_health MODE: MODE "reachable" accepts any HTTP answer, MODE "healthy"
# waits for 200. /health answers 503 until a layout exists and quorum is reached.
wait_for_health() {
  mode="$1"
  attempt=0
  while :; do
    code="$(curl -sS -o /dev/null -w '%{http_code}' "$ADMIN_URL/health" || true)"
    if [ "$mode" = "reachable" ] && [ -n "$code" ] && [ "$code" != "000" ]; then
      return 0
    fi
    if [ "$mode" = "healthy" ] && [ "$code" = "200" ]; then
      return 0
    fi
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 60 ]; then
      echo "garage admin API at $ADMIN_URL did not become $mode" >&2
      exit 1
    fi
    sleep 1
  done
}

wait_for_health reachable

status="$(api GET /v2/GetClusterStatus)"
node_id="$(printf '%s' "$status" | first_id)"
if [ -z "$node_id" ]; then
  echo "could not read the Garage node id" >&2
  exit 1
fi

layout="$(api GET /v2/GetClusterLayout)"
case "$layout" in
  *'"roles":[]'*)
    layout_version="$(printf '%s' "$status" | grep -o '"layoutVersion":[0-9]*' | cut -d: -f2)"
    api POST /v2/UpdateClusterLayout \
      "{\"roles\":[{\"id\":\"$node_id\",\"zone\":\"$ZONE\",\"capacity\":$CAPACITY,\"tags\":[\"dev\"]}]}" >/dev/null
    api POST /v2/ApplyClusterLayout "{\"version\":$((layout_version + 1))}" >/dev/null
    echo "garage layout applied for node $node_id"
    ;;
  *)
    echo "garage layout already present"
    ;;
esac

# Buckets and keys are refused until the layout is in effect across the cluster.
wait_for_health healthy

if ! api GET "/v2/GetBucketInfo?globalAlias=$BUCKET" >/dev/null 2>&1; then
  api POST /v2/CreateBucket "{\"globalAlias\":\"$BUCKET\"}" >/dev/null
  echo "created bucket $BUCKET"
fi
bucket_info="$(api GET "/v2/GetBucketInfo?globalAlias=$BUCKET")"
bucket_id="$(printf '%s' "$bucket_info" | first_id)"
if [ -z "$bucket_id" ]; then
  echo "could not read the id of bucket $BUCKET" >&2
  exit 1
fi

if ! api GET "/v2/GetKeyInfo?id=$KEY_ID" >/dev/null 2>&1; then
  api POST /v2/ImportKey \
    "{\"accessKeyId\":\"$KEY_ID\",\"secretAccessKey\":\"$KEY_SECRET\",\"name\":\"menura-dev\"}" >/dev/null
  echo "imported development key"
fi

api POST /v2/AllowBucketKey \
  "{\"bucketId\":\"$bucket_id\",\"accessKeyId\":\"$KEY_ID\",\"permissions\":{\"read\":true,\"write\":true,\"owner\":true}}" >/dev/null
echo "garage bootstrap done: bucket $BUCKET"

# Stay up after a successful bootstrap: `docker compose up --wait` reports a container
# that has exited as a failure, and the healthcheck of garage-init passes only on this marker.
touch /tmp/garage-bootstrap-done
trap 'exit 0' TERM INT
while :; do sleep 3600 & wait $!; done
