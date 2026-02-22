#!/usr/bin/env bash

set -euo pipefail

if ! command -v jq >/dev/null 2>&1; then
  echo "[FATAL] jq not found"
  exit 1
fi

if [ "$#" -lt 2 ] || [ "$#" -gt 4 ]; then
  echo "Usage: $0 <atomic_summary.json> <separated_summary.json> [atomic_label] [separated_label]"
  exit 1
fi

ATOMIC_FILE="$1"
SEPARATED_FILE="$2"
ATOMIC_LABEL="${3:-atomic}"
SEPARATED_LABEL="${4:-separated}"

if [ ! -f "$ATOMIC_FILE" ]; then
  echo "[FATAL] file not found: $ATOMIC_FILE"
  exit 1
fi

if [ ! -f "$SEPARATED_FILE" ]; then
  echo "[FATAL] file not found: $SEPARATED_FILE"
  exit 1
fi

extract_metric() {
  local file="$1"
  local jq_expr="$2"
  jq -r "$jq_expr // empty" "$file"
}

print_row() {
  local label="$1"
  local file="$2"

  local scenario attempts success conflict violation unexpected p95 throughput pass planned
  scenario="$(extract_metric "$file" '.scenario')"
  attempts="$(extract_metric "$file" '.totals.attempts')"
  success="$(extract_metric "$file" '.totals.status201')"
  conflict="$(extract_metric "$file" '.totals.status409')"
  violation="$(extract_metric "$file" '.totals.status422')"
  unexpected="$(extract_metric "$file" '.totals.unexpected')"
  p95="$(extract_metric "$file" '.latencyMs.p95')"
  throughput="$(extract_metric "$file" '.throughputRps')"
  pass="$(extract_metric "$file" '.domainAssertions.pass')"
  planned="$(extract_metric "$file" '.workload.plannedAttempts')"

  printf "%-14s %-24s %10s %10s %10s %10s %10s %12s %14s %8s\n" \
    "$label" "$scenario" "$attempts" "$success" "$conflict" "$violation" "$unexpected" "$p95" "$throughput" "$pass"

  if [ -n "$planned" ]; then
    printf "  %-12s plannedAttempts=%s\n" "$label" "$planned"
  fi
}

atomic_p95="$(extract_metric "$ATOMIC_FILE" '.latencyMs.p95')"
separated_p95="$(extract_metric "$SEPARATED_FILE" '.latencyMs.p95')"
atomic_tps="$(extract_metric "$ATOMIC_FILE" '.throughputRps')"
separated_tps="$(extract_metric "$SEPARATED_FILE" '.throughputRps')"

printf "\n== k6 summary comparison ==\n"
printf "%-14s %-24s %10s %10s %10s %10s %10s %12s %14s %8s\n" \
  "label" "scenario" "attempts" "status201" "status409" "status422" "unexpected" "p95(ms)" "throughput" "pass"
printf "%-14s %-24s %10s %10s %10s %10s %10s %12s %14s %8s\n" \
  "--------------" "------------------------" "----------" "----------" "----------" "----------" "----------" "------------" "--------------" "--------"
print_row "$ATOMIC_LABEL" "$ATOMIC_FILE"
print_row "$SEPARATED_LABEL" "$SEPARATED_FILE"

printf "\n== delta (separated - atomic) ==\n"
jq -nr \
  --arg atomic_p95 "$atomic_p95" \
  --arg separated_p95 "$separated_p95" \
  --arg atomic_tps "$atomic_tps" \
  --arg separated_tps "$separated_tps" \
  '
    def to_num($v):
      try ($v | tonumber) catch 0;
    {
      p95_delta_ms: (to_num($separated_p95) - to_num($atomic_p95)),
      throughput_delta_rps: (to_num($separated_tps) - to_num($atomic_tps))
    }
  ' | jq .
