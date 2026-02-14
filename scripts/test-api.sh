#!/usr/bin/env bash

set -uo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ENROLL_STRATEGY="${ENROLL_STRATEGY:-atomic}"
PARALLEL_REQUESTS="${PARALLEL_REQUESTS:-20}"

PASS_COUNT=0
FAIL_COUNT=0
TMP_DIR="$(mktemp -d)"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if ! command -v curl >/dev/null 2>&1; then
  echo "[FATAL] curl not found"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "[FATAL] jq not found"
  exit 1
fi

if ! [[ "$PARALLEL_REQUESTS" =~ ^[0-9]+$ ]] || [ "$PARALLEL_REQUESTS" -lt 2 ]; then
  echo "[FATAL] PARALLEL_REQUESTS must be an integer >= 2"
  exit 1
fi

resolve_enroll_path() {
  case "$ENROLL_STRATEGY" in
    default)
      echo "/enrollments"
      ;;
    pessimistic)
      echo "/enrollments/pessimistic"
      ;;
    optimistic)
      echo "/enrollments/optimistic"
      ;;
    atomic)
      echo "/enrollments/atomic"
      ;;
    *)
      echo ""
      ;;
  esac
}

ENROLL_PATH="$(resolve_enroll_path)"
if [ -z "$ENROLL_PATH" ]; then
  echo "[FATAL] ENROLL_STRATEGY must be one of: default|pessimistic|optimistic|atomic"
  exit 1
fi

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local out_file="$4"
  local code

  if [ -n "$body" ]; then
    code="$(curl -sS -o "$out_file" -w "%{http_code}" -X "$method" \
      "$BASE_URL$path" \
      -H 'Content-Type: application/json' \
      -d "$body" || echo "000")"
  else
    code="$(curl -sS -o "$out_file" -w "%{http_code}" -X "$method" \
      "$BASE_URL$path" || echo "000")"
  fi

  echo "$code"
}

assert_status() {
  local name="$1"
  local expected="$2"
  local actual="$3"

  if [ "$actual" = "$expected" ]; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "[PASS] $name expected=$expected actual=$actual"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "[FAIL] $name expected=$expected actual=$actual"
  fi
}

assert_json_field_exists() {
  local name="$1"
  local jq_expr="$2"
  local file="$3"

  if jq -e "$jq_expr" "$file" >/dev/null 2>&1; then
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "[PASS] $name"
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "[FAIL] $name"
  fi
}

echo "== Configuration =="
echo "BASE_URL=$BASE_URL"
echo "ENROLL_STRATEGY=$ENROLL_STRATEGY"
echo "ENROLL_PATH=$ENROLL_PATH"
echo "PARALLEL_REQUESTS=$PARALLEL_REQUESTS"

HEALTH_FILE="$TMP_DIR/health.json"
STUDENTS_FILE="$TMP_DIR/students.json"
COURSES_FILE="$TMP_DIR/courses.json"
PROFESSORS_FILE="$TMP_DIR/professors.json"

code="$(request GET /health "" "$HEALTH_FILE")"
assert_status "health check" "200" "$code"
assert_json_field_exists "health.status exists" '.status == "UP"' "$HEALTH_FILE"

code="$(request GET "/students?limit=5" "" "$STUDENTS_FILE")"
assert_status "students list" "200" "$code"
assert_json_field_exists "students has item" 'length > 0' "$STUDENTS_FILE"

code="$(request GET "/courses?limit=5" "" "$COURSES_FILE")"
assert_status "courses list" "200" "$code"
assert_json_field_exists "courses has item" 'length > 0' "$COURSES_FILE"

code="$(request GET "/professors?limit=5" "" "$PROFESSORS_FILE")"
assert_status "professors list" "200" "$code"
assert_json_field_exists "professors has item" 'length > 0' "$PROFESSORS_FILE"

STUDENT_ID="$(jq -r '.[0].id' "$STUDENTS_FILE")"
PARALLEL_STUDENT_ID="$(jq -r '.[1].id // .[0].id' "$STUDENTS_FILE")"
COURSE_ID="$(jq -r '.[0].id' "$COURSES_FILE")"
PARALLEL_COURSE_ID="$(jq -r '.[1].id // .[0].id' "$COURSES_FILE")"

if [ -z "$STUDENT_ID" ] || [ "$STUDENT_ID" = "null" ] || [ -z "$COURSE_ID" ] || [ "$COURSE_ID" = "null" ]; then
  echo "[FATAL] could not resolve sample student/course id"
  exit 1
fi

echo "Sample IDs: student=$STUDENT_ID course=$COURSE_ID parallelStudent=$PARALLEL_STUDENT_ID parallelCourse=$PARALLEL_COURSE_ID"

ENROLL_FILE="$TMP_DIR/enroll.json"
DUP_FILE="$TMP_DIR/duplicate.json"
TIMETABLE_FILE="$TMP_DIR/timetable.json"
CANCEL1_FILE="$TMP_DIR/cancel1.json"
CANCEL2_FILE="$TMP_DIR/cancel2.json"
BAD_FILE="$TMP_DIR/bad.json"
NOTFOUND_FILE="$TMP_DIR/notfound.json"

ENROLL_BODY="{\"studentId\":$STUDENT_ID,\"courseId\":$COURSE_ID}"
code="$(request POST "$ENROLL_PATH" "$ENROLL_BODY" "$ENROLL_FILE")"
assert_status "enroll success" "201" "$code"
assert_json_field_exists "enroll response has enrollmentId" '.enrollmentId != null' "$ENROLL_FILE"

code="$(request POST "$ENROLL_PATH" "$ENROLL_BODY" "$DUP_FILE")"
assert_status "duplicate enroll" "409" "$code"
assert_json_field_exists "duplicate code" '.code == "DUPLICATE_ENROLLMENT"' "$DUP_FILE"

code="$(request GET "/students/$STUDENT_ID/timetable" "" "$TIMETABLE_FILE")"
assert_status "timetable" "200" "$code"
assert_json_field_exists "timetable studentId" ".studentId == $STUDENT_ID" "$TIMETABLE_FILE"

ENROLLMENT_ID="$(jq -r '.enrollmentId' "$ENROLL_FILE")"
code="$(request DELETE "/enrollments/$ENROLLMENT_ID" "" "$CANCEL1_FILE")"
assert_status "cancel enrollment" "204" "$code"

code="$(request DELETE "/enrollments/$ENROLLMENT_ID" "" "$CANCEL2_FILE")"
assert_status "cancel again" "409" "$code"
assert_json_field_exists "cancel again code" '.code == "ENROLLMENT_CANCELLATION_NOT_ALLOWED"' "$CANCEL2_FILE"

code="$(request POST /enrollments '{"courseId":1}' "$BAD_FILE")"
assert_status "bad request missing studentId" "400" "$code"
assert_json_field_exists "bad request code" '.code == "BAD_REQUEST"' "$BAD_FILE"

code="$(request POST /enrollments '{"studentId":1,"courseId":999999999}' "$NOTFOUND_FILE")"
assert_status "not found" "404" "$code"
assert_json_field_exists "not found code" '.code == "COURSE_NOT_FOUND"' "$NOTFOUND_FILE"

# 병렬 경합: 동일 학생 + 동일 강좌를 동시에 요청해서 성공 건수가 1건을 넘지 않는지 확인.
PARALLEL_BODY="{\"studentId\":$PARALLEL_STUDENT_ID,\"courseId\":$PARALLEL_COURSE_ID}"
PARALLEL_CODES_FILE="$TMP_DIR/parallel-codes.txt"

seq 1 "$PARALLEL_REQUESTS" | xargs -P "$PARALLEL_REQUESTS" -I{} \
  curl -sS -o /dev/null -w "%{http_code}\n" -X POST "$BASE_URL$ENROLL_PATH" \
  -H 'Content-Type: application/json' -d "$PARALLEL_BODY" > "$PARALLEL_CODES_FILE"

PARALLEL_SUCCESS_COUNT="$(grep -c '^201$' "$PARALLEL_CODES_FILE" || true)"
PARALLEL_FAILURE_COUNT="$(grep -vc '^201$' "$PARALLEL_CODES_FILE" || true)"

echo "Parallel result: success=$PARALLEL_SUCCESS_COUNT failure=$PARALLEL_FAILURE_COUNT"

if [ "$PARALLEL_SUCCESS_COUNT" -le 1 ]; then
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "[PASS] parallel duplicate race keeps success <= 1"
else
  FAIL_COUNT=$((FAIL_COUNT + 1))
  echo "[FAIL] parallel duplicate race success=$PARALLEL_SUCCESS_COUNT (expected <= 1)"
fi

echo "== Summary =="
echo "PASS=$PASS_COUNT"
echo "FAIL=$FAIL_COUNT"

if [ "$FAIL_COUNT" -gt 0 ]; then
  exit 1
fi

exit 0
