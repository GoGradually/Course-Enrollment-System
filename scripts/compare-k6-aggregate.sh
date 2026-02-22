#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/compare-k6-aggregate.sh \
    <atomic_aggregate.json> \
    <separated_aggregate.json> \
    [atomic_label] \
    [separated_label] \
    [output_json]
USAGE
}

fatal() {
  echo "[FATAL] $*" >&2
  exit 1
}

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || fatal "Required command not found: $cmd"
}

extract_metric() {
  local file="$1"
  local jq_expr="$2"
  jq -r "$jq_expr // empty" "$file"
}

require_cmd jq

if [[ $# -lt 2 ]] || [[ $# -gt 5 ]]; then
  usage
  exit 1
fi

ATOMIC_FILE="$1"
SEPARATED_FILE="$2"
ATOMIC_LABEL="${3:-atomic}"
SEPARATED_LABEL="${4:-separated}"
OUTPUT_PATH="${5:-}"

[[ -f "$ATOMIC_FILE" ]] || fatal "file not found: $ATOMIC_FILE"
[[ -f "$SEPARATED_FILE" ]] || fatal "file not found: $SEPARATED_FILE"

atomic_profile="$(extract_metric "$ATOMIC_FILE" '.profile')"
atomic_scenario="$(extract_metric "$ATOMIC_FILE" '.scenario')"
atomic_runs="$(extract_metric "$ATOMIC_FILE" '.input.runCount')"
atomic_p95_median="$(extract_metric "$ATOMIC_FILE" '.metrics.latencyMsP95.median')"
atomic_p95_iqr="$(extract_metric "$ATOMIC_FILE" '.metrics.latencyMsP95.iqr')"
atomic_p95_cv="$(extract_metric "$ATOMIC_FILE" '.metrics.latencyMsP95.cv')"
atomic_throughput_median="$(extract_metric "$ATOMIC_FILE" '.metrics.throughputRps.median')"
atomic_throughput_iqr="$(extract_metric "$ATOMIC_FILE" '.metrics.throughputRps.iqr')"
atomic_throughput_cv="$(extract_metric "$ATOMIC_FILE" '.metrics.throughputRps.cv')"
atomic_stable="$(extract_metric "$ATOMIC_FILE" '.stability.pass')"

separated_profile="$(extract_metric "$SEPARATED_FILE" '.profile')"
separated_scenario="$(extract_metric "$SEPARATED_FILE" '.scenario')"
separated_runs="$(extract_metric "$SEPARATED_FILE" '.input.runCount')"
separated_p95_median="$(extract_metric "$SEPARATED_FILE" '.metrics.latencyMsP95.median')"
separated_p95_iqr="$(extract_metric "$SEPARATED_FILE" '.metrics.latencyMsP95.iqr')"
separated_p95_cv="$(extract_metric "$SEPARATED_FILE" '.metrics.latencyMsP95.cv')"
separated_throughput_median="$(extract_metric "$SEPARATED_FILE" '.metrics.throughputRps.median')"
separated_throughput_iqr="$(extract_metric "$SEPARATED_FILE" '.metrics.throughputRps.iqr')"
separated_throughput_cv="$(extract_metric "$SEPARATED_FILE" '.metrics.throughputRps.cv')"
separated_stable="$(extract_metric "$SEPARATED_FILE" '.stability.pass')"

printf "\n== k6 aggregate comparison ==\n"
printf "%-14s %-16s %-24s %6s %12s %12s %10s %14s %14s %10s %8s\n" \
  "label" "profile" "scenario" "runs" "p95_med(ms)" "p95_iqr" "p95_cv" "thr_med(rps)" "thr_iqr" "thr_cv" "stable"
printf "%-14s %-16s %-24s %6s %12s %12s %10s %14s %14s %10s %8s\n" \
  "--------------" "----------------" "------------------------" "------" "------------" "------------" "----------" "--------------" "--------------" "----------" "--------"
printf "%-14s %-16s %-24s %6s %12s %12s %10s %14s %14s %10s %8s\n" \
  "$ATOMIC_LABEL" "$atomic_profile" "$atomic_scenario" "$atomic_runs" \
  "$atomic_p95_median" "$atomic_p95_iqr" "$atomic_p95_cv" \
  "$atomic_throughput_median" "$atomic_throughput_iqr" "$atomic_throughput_cv" "$atomic_stable"
printf "%-14s %-16s %-24s %6s %12s %12s %10s %14s %14s %10s %8s\n" \
  "$SEPARATED_LABEL" "$separated_profile" "$separated_scenario" "$separated_runs" \
  "$separated_p95_median" "$separated_p95_iqr" "$separated_p95_cv" \
  "$separated_throughput_median" "$separated_throughput_iqr" "$separated_throughput_cv" "$separated_stable"

DELTA_JSON="$(jq -nr \
  --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg atomicLabel "$ATOMIC_LABEL" \
  --arg separatedLabel "$SEPARATED_LABEL" \
  --arg atomicFile "$ATOMIC_FILE" \
  --arg separatedFile "$SEPARATED_FILE" \
  --arg atomicProfile "$atomic_profile" \
  --arg separatedProfile "$separated_profile" \
  --arg atomicScenario "$atomic_scenario" \
  --arg separatedScenario "$separated_scenario" \
  --arg atomicRuns "$atomic_runs" \
  --arg separatedRuns "$separated_runs" \
  --arg atomicP95Median "$atomic_p95_median" \
  --arg separatedP95Median "$separated_p95_median" \
  --arg atomicP95Iqr "$atomic_p95_iqr" \
  --arg separatedP95Iqr "$separated_p95_iqr" \
  --arg atomicThroughputMedian "$atomic_throughput_median" \
  --arg separatedThroughputMedian "$separated_throughput_median" \
  --arg atomicThroughputIqr "$atomic_throughput_iqr" \
  --arg separatedThroughputIqr "$separated_throughput_iqr" \
  --arg atomicStable "$atomic_stable" \
  --arg separatedStable "$separated_stable" \
  '
    def to_num($v):
      try ($v | tonumber) catch 0;

    (to_num($atomicP95Median)) as $atomicP95
    | (to_num($separatedP95Median)) as $separatedP95
    | (to_num($atomicThroughputMedian)) as $atomicThroughput
    | (to_num($separatedThroughputMedian)) as $separatedThroughput
    | {
        generatedAt: $generatedAt,
        left: {
          label: $atomicLabel,
          file: $atomicFile,
          profile: $atomicProfile,
          scenario: $atomicScenario,
          runs: to_num($atomicRuns),
          p95MedianMs: $atomicP95,
          p95IqrMs: to_num($atomicP95Iqr),
          throughputMedianRps: $atomicThroughput,
          throughputIqrRps: to_num($atomicThroughputIqr),
          stable: ($atomicStable == "true")
        },
        right: {
          label: $separatedLabel,
          file: $separatedFile,
          profile: $separatedProfile,
          scenario: $separatedScenario,
          runs: to_num($separatedRuns),
          p95MedianMs: $separatedP95,
          p95IqrMs: to_num($separatedP95Iqr),
          throughputMedianRps: $separatedThroughput,
          throughputIqrRps: to_num($separatedThroughputIqr),
          stable: ($separatedStable == "true")
        },
        delta: {
          p95DeltaMs: ($separatedP95 - $atomicP95),
          p95DeltaPct: (if $atomicP95 == 0 then null else ((($separatedP95 - $atomicP95) / $atomicP95) * 100) end),
          throughputDeltaRps: ($separatedThroughput - $atomicThroughput),
          throughputDeltaPct: (if $atomicThroughput == 0 then null else ((($separatedThroughput - $atomicThroughput) / $atomicThroughput) * 100) end)
        }
      }
  ')"

printf "\n== delta (separated - atomic, median 기준) ==\n"
echo "$DELTA_JSON" | jq '.delta'

if [[ -n "$OUTPUT_PATH" ]]; then
  mkdir -p "$(dirname "$OUTPUT_PATH")"
  echo "$DELTA_JSON" | jq '.' > "$OUTPUT_PATH"
  printf "output=%s\n" "$OUTPUT_PATH"
fi
