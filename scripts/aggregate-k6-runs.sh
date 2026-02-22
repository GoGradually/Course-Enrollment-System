#!/usr/bin/env bash

set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/aggregate-k6-runs.sh \
    --input-dir <dir> \
    --output <aggregate.json> \
    [--cv-threshold <number>] \
    [--scenario <name>] \
    [--profile <name>]

Options:
  --input-dir      Directory containing run-*.summary.json files
  --output         Output aggregate JSON path
  --cv-threshold   CV threshold for stability gate (default: 0.10)
  --scenario       Optional scenario override
  --profile        Optional profile override
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

is_non_negative_number() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

extract_metric() {
  local file="$1"
  local jq_expr="$2"
  jq -r "$jq_expr // empty" "$file"
}

require_cmd jq

INPUT_DIR=""
OUTPUT_PATH=""
CV_THRESHOLD="0.10"
SCENARIO_OVERRIDE=""
PROFILE_OVERRIDE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --input-dir)
      [[ $# -ge 2 ]] || fatal "--input-dir requires a value"
      INPUT_DIR="$2"
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || fatal "--output requires a value"
      OUTPUT_PATH="$2"
      shift 2
      ;;
    --cv-threshold)
      [[ $# -ge 2 ]] || fatal "--cv-threshold requires a value"
      CV_THRESHOLD="$2"
      shift 2
      ;;
    --scenario)
      [[ $# -ge 2 ]] || fatal "--scenario requires a value"
      SCENARIO_OVERRIDE="$2"
      shift 2
      ;;
    --profile)
      [[ $# -ge 2 ]] || fatal "--profile requires a value"
      PROFILE_OVERRIDE="$2"
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

[[ -n "$INPUT_DIR" ]] || {
  usage
  fatal "--input-dir is required"
}
[[ -n "$OUTPUT_PATH" ]] || {
  usage
  fatal "--output is required"
}
[[ -d "$INPUT_DIR" ]] || fatal "Input directory not found: $INPUT_DIR"
is_non_negative_number "$CV_THRESHOLD" || fatal "--cv-threshold must be a non-negative number"

mapfile -t SUMMARY_FILES < <(find "$INPUT_DIR" -maxdepth 1 -type f -name 'run-*.summary.json' | sort)
[[ ${#SUMMARY_FILES[@]} -gt 0 ]] || fatal "No run-*.summary.json files found in: $INPUT_DIR"

for summary_file in "${SUMMARY_FILES[@]}"; do
  jq -e type "$summary_file" >/dev/null 2>&1 || fatal "Invalid JSON: $summary_file"
done

FILES_JSON="$(printf '%s\n' "${SUMMARY_FILES[@]}" | jq -R . | jq -s .)"
GENERATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mkdir -p "$(dirname "$OUTPUT_PATH")"

jq -s \
  --arg generated_at "$GENERATED_AT" \
  --arg input_dir "$INPUT_DIR" \
  --arg scenario_override "$SCENARIO_OVERRIDE" \
  --arg profile_override "$PROFILE_OVERRIDE" \
  --argjson cv_threshold "$CV_THRESHOLD" \
  --argjson files "$FILES_JSON" \
  '
    def mean:
      if length == 0 then 0 else (add / length) end;

    def quantile($p):
      if length == 0 then 0
      else
        (sort) as $s
        | ($s | length) as $n
        | if $n == 1 then
            $s[0]
          else
            (($n - 1) * $p) as $pos
            | ($pos | floor) as $lo
            | ($pos | ceil) as $hi
            | if $lo == $hi then
                $s[$lo]
              else
                $s[$lo] + (($s[$hi] - $s[$lo]) * ($pos - $lo))
              end
          end
      end;

    def sample_stddev:
      if length < 2 then 0
      else
        (mean) as $m
        | (map((. - $m) * (. - $m)) | add / (length - 1) | sqrt)
      end;

    def stats:
      . as $vals
      | ($vals | sort) as $sorted
      | ($vals | mean) as $mean
      | ($vals | quantile(0.25)) as $q1
      | ($vals | quantile(0.5)) as $median
      | ($vals | quantile(0.75)) as $q3
      | ($vals | sample_stddev) as $stddev
      | {
          count: ($vals | length),
          values: $vals,
          min: $sorted[0],
          max: $sorted[-1],
          mean: $mean,
          median: $median,
          q1: $q1,
          q3: $q3,
          iqr: ($q3 - $q1),
          stddev: $stddev,
          cv: (if $mean == 0 then 0 else ($stddev / $mean) end)
        };

    def scenario_name:
      if $scenario_override != "" then $scenario_override else (.[0].scenario // "unknown") end;

    def profile_name:
      if $profile_override != "" then
        $profile_override
      else
        (scenario_name) as $s
        | if ($s | startswith("rc-")) then
            "read-committed"
          elif ($s | startswith("rr-")) then
            "repeatable-read"
          else
            "unknown"
          end
      end;

    def attempts_matched:
      (.totals.attempts // null) as $attempts
      | (.workload.plannedAttempts // null) as $planned
      | (.totals.expectedAttempts // null) as $expected
      | if $attempts == null then
          false
        elif $planned != null then
          ($attempts == $planned)
        elif $expected != null then
          ($attempts == $expected)
        else
          true
        end;

    def numeric_values($path):
      map(
        (getpath($path)) as $v
        | if ($v | type) == "number" then $v else error("Missing numeric field: " + ($path | join("."))) end
      );

    . as $runs
    | {
        generatedAt: $generated_at,
        profile: profile_name,
        scenario: scenario_name,
        input: {
          dir: $input_dir,
          files: $files,
          runCount: ($runs | length)
        },
        consistency: {
          domainAssertionsPassAll: ($runs | all(.domainAssertions.pass == true)),
          attemptsMatchedAll: ($runs | all(attempts_matched)),
          attempts: ($runs | map(.totals.attempts // 0)),
          plannedAttempts: ($runs | map(.workload.plannedAttempts // null)),
          expectedAttempts: ($runs | map(.totals.expectedAttempts // null))
        },
        metrics: {
          latencyMsAvg: ($runs | numeric_values(["latencyMs", "avg"]) | stats),
          latencyMsP95: ($runs | numeric_values(["latencyMs", "p95"]) | stats),
          latencyMsP99: ($runs | numeric_values(["latencyMs", "p99"]) | stats),
          throughputRps: ($runs | numeric_values(["throughputRps"]) | stats),
          successRate: ($runs | numeric_values(["ratios", "successRate"]) | stats)
        }
      }
    | .stability = {
        cvThreshold: $cv_threshold,
        p95Cv: .metrics.latencyMsP95.cv,
        throughputCv: .metrics.throughputRps.cv,
        p95Stable: (.metrics.latencyMsP95.cv <= $cv_threshold),
        throughputStable: (.metrics.throughputRps.cv <= $cv_threshold),
        pass: (
          .consistency.domainAssertionsPassAll
          and .consistency.attemptsMatchedAll
          and (.metrics.latencyMsP95.cv <= $cv_threshold)
          and (.metrics.throughputRps.cv <= $cv_threshold)
        )
      }
  ' "${SUMMARY_FILES[@]}" > "$OUTPUT_PATH"

scenario_name="$(extract_metric "$OUTPUT_PATH" '.scenario')"
run_count="$(extract_metric "$OUTPUT_PATH" '.input.runCount')"
p95_median="$(extract_metric "$OUTPUT_PATH" '.metrics.latencyMsP95.median')"
p95_iqr="$(extract_metric "$OUTPUT_PATH" '.metrics.latencyMsP95.iqr')"
p95_cv="$(extract_metric "$OUTPUT_PATH" '.metrics.latencyMsP95.cv')"
throughput_median="$(extract_metric "$OUTPUT_PATH" '.metrics.throughputRps.median')"
throughput_iqr="$(extract_metric "$OUTPUT_PATH" '.metrics.throughputRps.iqr')"
throughput_cv="$(extract_metric "$OUTPUT_PATH" '.metrics.throughputRps.cv')"
stable="$(extract_metric "$OUTPUT_PATH" '.stability.pass')"

printf "\n== k6 aggregate ==\n"
printf "scenario=%s runs=%s stable=%s\n" "$scenario_name" "$run_count" "$stable"
printf "p95(ms): median=%s iqr=%s cv=%s\n" "$p95_median" "$p95_iqr" "$p95_cv"
printf "throughput(req/s): median=%s iqr=%s cv=%s\n" "$throughput_median" "$throughput_iqr" "$throughput_cv"
printf "output=%s\n" "$OUTPUT_PATH"
