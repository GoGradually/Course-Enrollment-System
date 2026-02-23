#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/compare-k6-single-summary.sh [options]

Options:
  --root <path>        Root directory for single summary files
                       (default: performance/k6/results/single)
  --profile <name>     read-committed | repeatable-read | all (default: all)
  --output-dir <path>  Output directory for comparison JSON
                       (default: performance/k6/results/single/comparison)
  -h, --help           Show help
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

ROOT_DIR="performance/k6/results/single"
PROFILE="all"
OUTPUT_DIR="performance/k6/results/single/comparison"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --root)
      [[ $# -ge 2 ]] || fatal "--root requires a value"
      ROOT_DIR="$2"
      shift 2
      ;;
    --profile)
      [[ $# -ge 2 ]] || fatal "--profile requires a value"
      PROFILE="$2"
      shift 2
      ;;
    --output-dir)
      [[ $# -ge 2 ]] || fatal "--output-dir requires a value"
      OUTPUT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fatal "Unknown argument: $1"
      ;;
  esac
done

[[ -d "$ROOT_DIR" ]] || fatal "Root directory not found: $ROOT_DIR"

case "$PROFILE" in
  read-committed|repeatable-read|all)
    ;;
  *)
    fatal "--profile must be one of: read-committed, repeatable-read, all"
    ;;
esac

mkdir -p "$OUTPUT_DIR"

build_profile_json() {
  local profile="$1"
  local prefix="$2"
  local optimistic_file="$ROOT_DIR/${prefix}-optimistic.summary.json"
  local pessimistic_file="$ROOT_DIR/${prefix}-pessimistic.summary.json"
  local separated_file="$ROOT_DIR/${prefix}-separated.summary.json"
  local atomic_file="$ROOT_DIR/${prefix}-atomic.summary.json"
  local output_file="$OUTPUT_DIR/${profile}-single-4strategy.json"

  for file in "$optimistic_file" "$pessimistic_file" "$separated_file" "$atomic_file"; do
    [[ -f "$file" ]] || fatal "Missing summary file: $file"
    jq -e type "$file" >/dev/null 2>&1 || fatal "Invalid JSON: $file"
  done

  jq -n \
    --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    --arg profile "$profile" \
    --arg root "$ROOT_DIR" \
    --slurpfile optimistic "$optimistic_file" \
    --slurpfile pessimistic "$pessimistic_file" \
    --slurpfile separated "$separated_file" \
    --slurpfile atomic "$atomic_file" \
    '
      def summary($s):
        {
          scenario: $s.scenario,
          latencyMs: {
            avg: $s.latencyMs.avg,
            p95: $s.latencyMs.p95,
            p99: $s.latencyMs.p99
          },
          throughputRps: $s.throughputRps,
          successRate: $s.ratios.successRate,
          pass: $s.domainAssertions.pass,
          totals: {
            attempts: $s.totals.attempts,
            status201: $s.totals.status201,
            status409: $s.totals.status409,
            status422: $s.totals.status422,
            unexpected: $s.totals.unexpected
          }
        };

      {
        generatedAt: $generatedAt,
        profile: $profile,
        sourceRoot: $root,
        strategies: {
          optimistic: summary($optimistic[0]),
          pessimistic: summary($pessimistic[0]),
          separated: summary($separated[0]),
          atomic: summary($atomic[0])
        }
      }
      | .ranking = {
          p95Ascending:
            (
              .strategies
              | to_entries
              | sort_by(.value.latencyMs.p95)
              | map({
                  strategy: .key,
                  scenario: .value.scenario,
                  p95: .value.latencyMs.p95
                })
            ),
          throughputDescending:
            (
              .strategies
              | to_entries
              | sort_by(-(.value.throughputRps))
              | map({
                  strategy: .key,
                  scenario: .value.scenario,
                  throughputRps: .value.throughputRps
                })
            )
        }
      | .consistency = {
          allPass: ([.strategies[].pass] | all),
          anyUnexpected: ([.strategies[].totals.unexpected] | any(. > 0)),
          attempts: (
            .strategies
            | to_entries
            | map({
                strategy: .key,
                attempts: .value.totals.attempts
              })
          )
        }
    ' > "$output_file"

  printf "\n== single 4-strategy comparison (%s) ==\n" "$profile"
  printf "%-12s %-18s %10s %10s %14s %14s %14s %8s\n" \
    "strategy" "scenario" "attempts" "p95(ms)" "avg(ms)" "throughput" "successRate" "pass"
  printf "%-12s %-18s %10s %10s %14s %14s %14s %8s\n" \
    "------------" "------------------" "----------" "----------" "--------------" "--------------" "--------------" "--------"

  while IFS=$'\t' read -r strategy scenario attempts p95 avg throughput success_rate pass; do
    printf "%-12s %-18s %10s %10s %14s %14s %14s %8s\n" \
      "$strategy" "$scenario" "$attempts" "$p95" "$avg" "$throughput" "$success_rate" "$pass"
  done < <(
    jq -r '
      .strategies
      | to_entries[]
      | [
          .key,
          .value.scenario,
          .value.totals.attempts,
          .value.latencyMs.p95,
          .value.latencyMs.avg,
          .value.throughputRps,
          .value.successRate,
          .value.pass
        ]
      | @tsv
    ' "$output_file"
  )

  local p95_best throughput_best
  p95_best="$(extract_metric "$output_file" '.ranking.p95Ascending[0].strategy')"
  throughput_best="$(extract_metric "$output_file" '.ranking.throughputDescending[0].strategy')"

  echo "bestByP95=$p95_best bestByThroughput=$throughput_best"
  echo "output=$output_file"
}

if [[ "$PROFILE" == "all" ]]; then
  build_profile_json "read-committed" "rc"
  build_profile_json "repeatable-read" "rr"
else
  if [[ "$PROFILE" == "read-committed" ]]; then
    build_profile_json "read-committed" "rc"
  else
    build_profile_json "repeatable-read" "rr"
  fi
fi
