#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/infra/docker-compose.yml")
API_URL="${PERFUSION_API_URL:-http://localhost:8080}"
TMP_DIR="$(mktemp -d)"
JOB_ID=""

cleanup() {
  "${COMPOSE[@]}" up -d rabbitmq worker worker-memory worker-compute >/dev/null 2>&1 || true
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

echo "[1/5] Starting the local PerfusionLab stack"
"${COMPOSE[@]}" up -d --build
for attempt in $(seq 1 60); do
  if curl -fsS "$API_URL/api/v1/health" >/dev/null; then break; fi
  sleep 2
done
if ! curl -fsS "$API_URL/api/v1/health" >/dev/null 2>&1; then
  echo "API did not become healthy; container diagnostics follow" >&2
  "${COMPOSE[@]}" ps >&2 || true
  "${COMPOSE[@]}" logs --no-color --tail=200 api postgres rabbitmq >&2 || true
  docker inspect perfusionlab-api --format 'API state={{.State.Status}} exit={{.State.ExitCode}} error={{.State.Error}}' >&2 || true
  exit 1
fi

echo "[2/5] Stopping RabbitMQ to create a broker outage"
"${COMPOSE[@]}" stop rabbitmq
sleep 3

echo "[3/5] Submitting a job while the broker is unavailable"
HTTP_CODE="$(curl -sS -o "$TMP_DIR/job.json" -w '%{http_code}' -X POST "$API_URL/api/v1/simulations/jobs" \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-ID: chaos-broker-restart-smoke' \
  -d '{"pumpFlowLMin":4.8,"hemoglobinGdL":10.0,"arterialSaturation":0.98,"systemicVascularResistance":1200.0,"bodySurfaceAreaM2":1.9,"temperatureC":35.5}')"
if [[ "$HTTP_CODE" != "202" ]]; then
  cat "$TMP_DIR/job.json" >&2
  echo "Expected HTTP 202, received $HTTP_CODE" >&2
  exit 1
fi
JOB_ID="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["jobId"])' "$TMP_DIR/job.json")"
echo "Created job $JOB_ID"

echo "[4/5] Restoring RabbitMQ; outbox and consumers should reconnect"
"${COMPOSE[@]}" up -d rabbitmq
for attempt in $(seq 1 60); do
  STATUS="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' perfusionlab-rabbitmq 2>/dev/null || echo starting)"
  [[ "$STATUS" == "healthy" ]] && break
  sleep 2
done
[[ "$STATUS" == "healthy" ]] || { echo "RabbitMQ did not become healthy" >&2; exit 1; }
"${COMPOSE[@]}" up -d worker worker-memory worker-compute

echo "[5/5] Waiting for the queued job to complete after broker recovery"
for attempt in $(seq 1 90); do
  BODY="$(curl -fsS "$API_URL/api/v1/simulations/jobs/$JOB_ID" || true)"
  STATE="$(python3 -c 'import json,sys; print(json.loads(sys.argv[1]).get("state","UNKNOWN"))' "$BODY" 2>/dev/null || echo UNKNOWN)"
  echo "Attempt $attempt: $STATE"
  if [[ "$STATE" == "SUCCEEDED" ]]; then
    echo "PASS: job completed after RabbitMQ outage and recovery."
    exit 0
  fi
  if [[ "$STATE" == "FAILED" ]]; then
    echo "Job failed after broker recovery:" >&2
    echo "$BODY" >&2
    "${COMPOSE[@]}" logs --no-color --tail=150 api worker worker-memory worker-compute >&2 || true
    exit 1
  fi
  sleep 2
done

"${COMPOSE[@]}" logs --no-color --tail=150 api worker worker-memory worker-compute >&2 || true
echo "Timed out waiting for job $JOB_ID" >&2
exit 1
