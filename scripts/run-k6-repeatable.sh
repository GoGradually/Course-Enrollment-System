#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

usage() {
  cat <<'USAGE'
Usage:
  scripts/run-k6-repeatable.sh [options]

Options:
  --runs <int>                    Number of repeated runs per scenario (default: 25)
  --profiles <csv>                Profiles: read-committed,repeatable-read (default: both)
  --scenarios <csv>               Scenario names without .js
                                  (default: rc-atomic-multi,rc-separated-multi,rr-atomic-multi,rr-separated-multi)
  --cv-threshold <number>         Stability CV threshold (default: 0.10)
  --fail-on-thresholds <bool>     Stop on k6 threshold crossing (default: false)
  --base-url <url>                Base URL for API and k6 (default: http://localhost:8080)
  --stabilize-seconds <int>       Wait time after health is up (default: 10)
  --health-timeout-seconds <int>  Health check timeout for server boot (default: 120)
  --result-root <path>            Root directory for repeatable results
                                  (default: performance/k6/results/repeatable)
  -h, --help                      Show help

Environment:
  Existing k6 env vars can be passed as-is:
  VUS, LOOPS, RAMP_UP_SECONDS, P95_MS, MAX_DURATION, FAIL_ON_ASSERTION,
  HOT_COURSE_ID, TARGET_CAPACITY,
  MULTI_COURSE_COUNT, MULTI_COMPETITION_MULTIPLIER, MULTI_MAX_TOTAL_ATTEMPTS,
  MULTI_INTERLEAVE_SEED, MULTI_REQUIRE_EMPTY_ENROLLED
USAGE
}

fatal() {
  echo "[FATAL] $*" >&2
  exit 1
}

info() {
  echo "[INFO] $*"
}

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || fatal "Required command not found: $cmd"
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

split_csv() {
  local csv="$1"
  local token
  local -n out_ref="$2"

  out_ref=()
  IFS=',' read -r -a raw_tokens <<< "$csv"
  for token in "${raw_tokens[@]}"; do
    token="$(trim "$token")"
    [[ -n "$token" ]] && out_ref+=("$token")
  done
}

is_positive_int() {
  local value="$1"
  [[ "$value" =~ ^[1-9][0-9]*$ ]]
}

is_non_negative_int() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+$ ]]
}

is_non_negative_number() {
  local value="$1"
  [[ "$value" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

normalize_bool() {
  local value="$1"
  value="$(echo "$value" | tr '[:upper:]' '[:lower:]')"
  case "$value" in
    1|true|yes|y|on)
      echo "true"
      ;;
    0|false|no|n|off)
      echo "false"
      ;;
    *)
      return 1
      ;;
  esac
}

scenario_expected_profile() {
  local scenario="$1"
  case "$scenario" in
    rc-*) echo "read-committed" ;;
    rr-*) echo "repeatable-read" ;;
    *) echo "" ;;
  esac
}

is_atomic_scenario() {
  local scenario="$1"
  [[ "$scenario" == *"-atomic-"* ]] || [[ "$scenario" == *"-atomic" ]]
}

is_separated_scenario() {
  local scenario="$1"
  [[ "$scenario" == *"-separated-"* ]] || [[ "$scenario" == *"-separated" ]]
}

extract_metric() {
  local file="$1"
  local jq_expr="$2"
  jq -r "$jq_expr // empty" "$file"
}

wait_for_readiness() {
  local base_url="$1"
  local timeout_seconds="$2"
  local server_pid="$3"
  local server_log="$4"
  local deadline=$((SECONDS + timeout_seconds))

  while ((SECONDS < deadline)); do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      echo "[ERROR] bootRun exited unexpectedly. profile=$ACTIVE_PROFILE pid=$server_pid"
      tail -n 60 "$server_log" || true
      return 1
    fi

    local health_status
    health_status="$(curl -sS -o /dev/null -w "%{http_code}" "$base_url/health" || true)"
    if [[ "$health_status" == "200" ]]; then
      local courses_body_file="/tmp/k6-readiness-courses-$$.json"
      local courses_status
      courses_status="$(curl -sS -o "$courses_body_file" -w "%{http_code}" "$base_url/courses?offset=0&limit=1" || true)"

      if [[ "$courses_status" == "200" ]]; then
        if jq -e 'type == "array" and length > 0' "$courses_body_file" >/dev/null 2>&1; then
          rm -f "$courses_body_file"
          return 0
        fi
      fi
      rm -f "$courses_body_file"
    fi
    sleep 1
  done

  echo "[ERROR] Readiness timeout after ${timeout_seconds}s. profile=$ACTIVE_PROFILE pid=$server_pid"
  echo "[ERROR] Readiness condition: /health=200 and /courses?offset=0&limit=1 returns non-empty array"
  tail -n 60 "$server_log" || true
  return 1
}

ACTIVE_SERVER_PID=""
ACTIVE_SERVER_LOG=""
ACTIVE_PROFILE=""

stop_server() {
  if [[ -z "$ACTIVE_SERVER_PID" ]]; then
    return 0
  fi

  if kill -0 "$ACTIVE_SERVER_PID" 2>/dev/null; then
    kill "$ACTIVE_SERVER_PID" 2>/dev/null || true
    for _ in $(seq 1 15); do
      if ! kill -0 "$ACTIVE_SERVER_PID" 2>/dev/null; then
        break
      fi
      sleep 1
    done
    if kill -0 "$ACTIVE_SERVER_PID" 2>/dev/null; then
      kill -9 "$ACTIVE_SERVER_PID" 2>/dev/null || true
    fi
    wait "$ACTIVE_SERVER_PID" 2>/dev/null || true
  fi

  ACTIVE_SERVER_PID=""
  ACTIVE_SERVER_LOG=""
  ACTIVE_PROFILE=""
}

cleanup_on_exit() {
  stop_server
}

trap cleanup_on_exit EXIT INT TERM

start_server() {
  local profile="$1"
  local log_path="$2"
  local base_url="$3"
  local timeout_seconds="$4"
  local stabilize_seconds="$5"

  stop_server
  mkdir -p "$(dirname "$log_path")"

  info "Starting server profile=$profile log=$log_path"
  SPRING_PROFILES_ACTIVE="$profile" ./gradlew bootRun > "$log_path" 2>&1 &
  ACTIVE_SERVER_PID="$!"
  ACTIVE_SERVER_LOG="$log_path"
  ACTIVE_PROFILE="$profile"

  if ! wait_for_readiness "$base_url" "$timeout_seconds" "$ACTIVE_SERVER_PID" "$ACTIVE_SERVER_LOG"; then
    return 1
  fi

  if (( stabilize_seconds > 0 )); then
    info "Stabilizing for ${stabilize_seconds}s"
    sleep "$stabilize_seconds"
  fi
}

run_k6_once() {
  local profile="$1"
  local scenario="$2"
  local run_index="$3"
  local run_width="$4"
  local base_url="$5"
  local health_timeout_seconds="$6"
  local stabilize_seconds="$7"
  local result_root="$8"

  local run_id
  run_id="$(printf "%0${run_width}d" "$run_index")"

  local raw_dir="$result_root/raw/$profile/$scenario"
  local log_dir="$result_root/logs/$profile/$scenario"
  local summary_path="$raw_dir/run-${run_id}.summary.json"
  local meta_path="$raw_dir/run-${run_id}.meta.json"
  local server_log="$log_dir/run-${run_id}.server.log"

  mkdir -p "$raw_dir" "$log_dir"

  local started_at ended_at started_epoch ended_epoch duration_seconds
  started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  started_epoch="$(date +%s)"

  start_server "$profile" "$server_log" "$base_url" "$health_timeout_seconds" "$stabilize_seconds"

  info "Running k6 scenario=$scenario run=${run_id}/${RUNS}"
  local k6_exit_code=0
  SUMMARY_PATH="$summary_path" BASE_URL="$base_url" k6 run "performance/k6/scenarios/${scenario}.js" || k6_exit_code=$?

  if (( k6_exit_code != 0 )); then
    if (( k6_exit_code == 99 )) && [[ "$FAIL_ON_THRESHOLDS" == "false" ]]; then
      info "k6 thresholds crossed (exit code 99). Continuing. scenario=$scenario run=$run_id"
    else
      stop_server
      fatal "k6 run failed. scenario=$scenario run=$run_id exitCode=$k6_exit_code"
    fi
  fi

  [[ -f "$summary_path" ]] || {
    stop_server
    fatal "Summary file missing: $summary_path"
  }

  jq -e type "$summary_path" >/dev/null 2>&1 || {
    stop_server
    fatal "Invalid summary JSON: $summary_path"
  }

  jq -e '.domainAssertions.pass == true' "$summary_path" >/dev/null 2>&1 || {
    stop_server
    fatal "domainAssertions.pass=false. scenario=$scenario run=$run_id summary=$summary_path"
  }

  jq -e '
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
      end
  ' "$summary_path" >/dev/null 2>&1 || {
    stop_server
    fatal "attempts mismatch. scenario=$scenario run=$run_id summary=$summary_path"
  }

  ended_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  ended_epoch="$(date +%s)"
  duration_seconds=$((ended_epoch - started_epoch))

  jq -n \
    --arg profile "$profile" \
    --arg scenario "$scenario" \
    --arg runId "$run_id" \
    --arg startedAt "$started_at" \
    --arg endedAt "$ended_at" \
    --argjson durationSeconds "$duration_seconds" \
    --arg baseUrl "$base_url" \
    --arg summaryPath "$summary_path" \
    --arg serverLog "$server_log" \
    --argjson pass true \
    '{
      profile: $profile,
      scenario: $scenario,
      runId: $runId,
      startedAt: $startedAt,
      endedAt: $endedAt,
      durationSeconds: $durationSeconds,
      baseUrl: $baseUrl,
      summaryPath: $summaryPath,
      serverLog: $serverLog,
      pass: $pass
    }' > "$meta_path"

  stop_server
}

require_cmd jq
require_cmd curl
require_cmd k6
k6_bin_path="$(command -v k6)"

RUNS=25
PROFILES_CSV="read-committed,repeatable-read"
SCENARIOS_CSV="rc-atomic-multi,rc-separated-multi,rr-atomic-multi,rr-separated-multi"
CV_THRESHOLD="0.10"
FAIL_ON_THRESHOLDS="false"
BASE_URL_DEFAULT="${BASE_URL:-http://localhost:8080}"
BASE_URL_VALUE="$BASE_URL_DEFAULT"
STABILIZE_SECONDS=10
HEALTH_TIMEOUT_SECONDS=120
RESULT_ROOT="performance/k6/results/repeatable"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --runs)
      [[ $# -ge 2 ]] || fatal "--runs requires a value"
      RUNS="$2"
      shift 2
      ;;
    --profiles)
      [[ $# -ge 2 ]] || fatal "--profiles requires a value"
      PROFILES_CSV="$2"
      shift 2
      ;;
    --scenarios)
      [[ $# -ge 2 ]] || fatal "--scenarios requires a value"
      SCENARIOS_CSV="$2"
      shift 2
      ;;
    --cv-threshold)
      [[ $# -ge 2 ]] || fatal "--cv-threshold requires a value"
      CV_THRESHOLD="$2"
      shift 2
      ;;
    --fail-on-thresholds)
      [[ $# -ge 2 ]] || fatal "--fail-on-thresholds requires a value"
      parsed_bool="$(normalize_bool "$2")" || fatal "--fail-on-thresholds must be true/false"
      FAIL_ON_THRESHOLDS="$parsed_bool"
      shift 2
      ;;
    --base-url)
      [[ $# -ge 2 ]] || fatal "--base-url requires a value"
      BASE_URL_VALUE="$2"
      shift 2
      ;;
    --stabilize-seconds)
      [[ $# -ge 2 ]] || fatal "--stabilize-seconds requires a value"
      STABILIZE_SECONDS="$2"
      shift 2
      ;;
    --health-timeout-seconds)
      [[ $# -ge 2 ]] || fatal "--health-timeout-seconds requires a value"
      HEALTH_TIMEOUT_SECONDS="$2"
      shift 2
      ;;
    --result-root)
      [[ $# -ge 2 ]] || fatal "--result-root requires a value"
      RESULT_ROOT="$2"
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

is_positive_int "$RUNS" || fatal "--runs must be a positive integer"
is_non_negative_int "$STABILIZE_SECONDS" || fatal "--stabilize-seconds must be a non-negative integer"
is_positive_int "$HEALTH_TIMEOUT_SECONDS" || fatal "--health-timeout-seconds must be a positive integer"
is_non_negative_number "$CV_THRESHOLD" || fatal "--cv-threshold must be a non-negative number"

if [[ "$k6_bin_path" == /snap/* ]] && [[ "$RESULT_ROOT" == /tmp/* ]]; then
  info "Detected snap k6 with /tmp result-root. Summary file write may fail due snap confinement."
  info "Use --result-root inside workspace if you see summary write errors."
fi

PROFILES=()
SCENARIOS=()
split_csv "$PROFILES_CSV" PROFILES
split_csv "$SCENARIOS_CSV" SCENARIOS

[[ ${#PROFILES[@]} -gt 0 ]] || fatal "No profiles to run"
[[ ${#SCENARIOS[@]} -gt 0 ]] || fatal "No scenarios to run"

for profile in "${PROFILES[@]}"; do
  case "$profile" in
    read-committed|repeatable-read)
      ;;
    *)
      fatal "Unsupported profile: $profile"
      ;;
  esac
done

for scenario in "${SCENARIOS[@]}"; do
  scenario_path="performance/k6/scenarios/${scenario}.js"
  [[ -f "$scenario_path" ]] || fatal "Scenario not found: $scenario_path"
  expected_profile="$(scenario_expected_profile "$scenario")"
  [[ -n "$expected_profile" ]] || fatal "Scenario prefix must start with rc- or rr-: $scenario"
done

RUN_WIDTH=2
if (( ${#RUNS} > RUN_WIDTH )); then
  RUN_WIDTH=${#RUNS}
fi

mkdir -p "$RESULT_ROOT/raw" "$RESULT_ROOT/logs" "$RESULT_ROOT/aggregated" "$RESULT_ROOT/comparison"

info "Repeatable run started"
info "runs=$RUNS profiles=${PROFILES[*]} scenarios=${SCENARIOS[*]}"
info "baseUrl=$BASE_URL_VALUE cvThreshold=$CV_THRESHOLD failOnThresholds=$FAIL_ON_THRESHOLDS resultRoot=$RESULT_ROOT"

for profile in "${PROFILES[@]}"; do
  PROFILE_SCENARIOS=()
  for scenario in "${SCENARIOS[@]}"; do
    expected_profile="$(scenario_expected_profile "$scenario")"
    if [[ "$expected_profile" == "$profile" ]]; then
      PROFILE_SCENARIOS+=("$scenario")
    fi
  done

  if [[ ${#PROFILE_SCENARIOS[@]} -eq 0 ]]; then
    info "No scenarios mapped to profile=$profile, skipping profile"
    continue
  fi

  atomic_aggregate=""
  separated_aggregate=""

  for scenario in "${PROFILE_SCENARIOS[@]}"; do
    info "Scenario start profile=$profile scenario=$scenario"
    for run_index in $(seq 1 "$RUNS"); do
      run_k6_once \
        "$profile" \
        "$scenario" \
        "$run_index" \
        "$RUN_WIDTH" \
        "$BASE_URL_VALUE" \
        "$HEALTH_TIMEOUT_SECONDS" \
        "$STABILIZE_SECONDS" \
        "$RESULT_ROOT"
    done

    aggregate_output="$RESULT_ROOT/aggregated/$profile/${scenario}.aggregate.json"
    "$SCRIPT_DIR/aggregate-k6-runs.sh" \
      --input-dir "$RESULT_ROOT/raw/$profile/$scenario" \
      --output "$aggregate_output" \
      --cv-threshold "$CV_THRESHOLD" \
      --profile "$profile" \
      --scenario "$scenario"

    if is_atomic_scenario "$scenario"; then
      atomic_aggregate="$aggregate_output"
    fi
    if is_separated_scenario "$scenario"; then
      separated_aggregate="$aggregate_output"
    fi
  done

  if [[ -n "$atomic_aggregate" ]] && [[ -n "$separated_aggregate" ]]; then
    comparison_output="$RESULT_ROOT/comparison/${profile}-atomic-vs-separated.aggregate.json"
    "$SCRIPT_DIR/compare-k6-aggregate.sh" \
      "$atomic_aggregate" \
      "$separated_aggregate" \
      "${profile}-atomic" \
      "${profile}-separated" \
      "$comparison_output"
  else
    info "No atomic/separated pair for profile=$profile, skipping aggregate comparison"
  fi
done

info "Repeatable run completed. Results at $RESULT_ROOT"
