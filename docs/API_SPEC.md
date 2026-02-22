# 수강신청 시스템 API 명세

Base URL: `http://localhost:8080`

공통 에러 응답:

```json
{
  "code": "COURSE_NOT_FOUND",
  "message": "Course not found. courseId=999",
  "timestamp": "2026-02-08T17:47:49.198956068+09:00"
}
```

## 1. Health

### GET `/health`

- 설명: 서버 정상 동작 여부 확인
- 성공: `200 OK`

응답 예시:

```json
{
  "status": "UP"
}
```

## 2. Students

### GET `/students`

- 설명: 학생 목록 조회
- Query
    - `offset` (optional, 기본 0)
    - `limit` (optional, 최대 100)
- 성공: `200 OK`

응답 예시:

```json
[
  {
    "id": 1,
    "studentNumber": "2026000001",
    "name": "장채정",
    "departmentId": 1,
    "departmentName": "컴퓨터공학과"
  }
]
```

## 3. Courses

### GET `/courses`

- 설명: 강좌 목록 조회 (전체/학과 필터)
- Query
    - `departmentId` (optional)
    - `offset` (optional, 기본 0)
    - `limit` (optional, 최대 100)
- 성공: `200 OK`

응답 예시:

```json
[
  {
    "id": 1,
    "courseCode": "CSE101",
    "name": "국어학개론 개론",
    "credits": 2,
    "capacity": 54,
    "enrolled": 0,
    "schedule": "MON 09:00-10:30",
    "departmentId": 1,
    "departmentName": "컴퓨터공학과",
    "professorId": 1,
    "professorName": "안준서"
  }
]
```

## 4. Professors

### GET `/professors`

- 설명: 교수 목록 조회
- Query
    - `offset` (optional, 기본 0)
    - `limit` (optional, 최대 100)
- 성공: `200 OK`

응답 예시:

```json
[
  {
    "id": 1,
    "name": "안준서",
    "departmentId": 1,
    "departmentName": "컴퓨터공학과"
  }
]
```

## 5. Enrollments

요청 본문(공통):

```json
{
  "studentId": 1,
  "courseId": 1
}
```

### POST `/enrollments`

- 설명: 수강신청(기본 전략)
- 성공: `201 Created`
- 실패:
    - `400 Bad Request` (필수 필드 누락/요청 형식 오류)
    - `404 Not Found` (학생/강좌 없음)
  - `409 Conflict` (중복 신청/동시성·락 충돌, 재시도 소진 포함)
    - `422 Unprocessable Entity` (학점 초과/시간표 충돌/정원 초과)

### POST `/enrollments/pessimistic`

- 설명: 비관적 락 전략 수강신청
- 응답 코드: `/enrollments`와 동일

### POST `/enrollments/optimistic`

- 설명: 낙관적 락 전략 수강신청
- 응답 코드: `/enrollments`와 동일

### POST `/enrollments/atomic`

- 설명: 원자적 업데이트 전략 수강신청
- 응답 코드: `/enrollments`와 동일

### POST `/enrollments/separated`

- 설명: 트랜잭션 분리 전략 수강신청
- 응답 코드: `/enrollments`와 동일

성공 응답 예시:

```json
{
  "enrollmentId": 1,
  "studentId": 1,
  "courseId": 1,
  "status": "ACTIVE"
}
```

에러 응답 예시:

- `409 Conflict` (중복 신청)

```json
{
  "code": "DUPLICATE_ENROLLMENT",
  "message": "Duplicate enrollment. studentId=1, courseId=1",
  "timestamp": "2026-02-08T17:47:49.141792872+09:00"
}
```

- `400 Bad Request` (필수값 누락)

```json
{
  "code": "BAD_REQUEST",
  "message": "studentId is required",
  "timestamp": "2026-02-08T17:47:49.191346584+09:00"
}
```

- `404 Not Found` (강좌 미존재)

```json
{
  "code": "COURSE_NOT_FOUND",
  "message": "Course not found. courseId=999999999",
  "timestamp": "2026-02-08T17:47:49.198956068+09:00"
}
```

### DELETE `/enrollments/{enrollmentId}`

- 설명: 수강취소
- 성공: `204 No Content`
- 실패:
    - `404 Not Found` (신청 정보 없음)
    - `409 Conflict` (이미 취소된 신청)

에러 응답 예시 (`409`):

```json
{
  "code": "ENROLLMENT_CANCELLATION_NOT_ALLOWED",
  "message": "Enrollment cancellation not allowed. enrollmentId=1",
  "timestamp": "2026-02-08T17:47:49.183342141+09:00"
}
```

## 6. Timetable

### GET `/students/{studentId}/timetable`

- 설명: 학생 시간표 조회
- 성공: `200 OK`
- 실패: `404 Not Found` (학생 없음)

응답 예시:

```json
{
  "studentId": 1,
  "totalCredits": 2,
  "courses": [
    {
      "courseId": 1,
      "courseName": "국어학개론 개론",
      "credits": 2,
      "schedule": "MON 09:00-10:30",
      "professorName": "안준서",
      "departmentName": "컴퓨터공학과"
    }
  ]
}
```

## 7. 수동 테스트용 curl 예시

```bash
curl -i http://localhost:8080/health
curl -s "http://localhost:8080/students?limit=5"
curl -s "http://localhost:8080/courses?limit=5"
curl -s "http://localhost:8080/professors?limit=5"
curl -s -X POST http://localhost:8080/enrollments/atomic \
  -H 'Content-Type: application/json' \
  -d '{"studentId":1,"courseId":1}'
curl -s -X POST http://localhost:8080/enrollments/separated \
  -H 'Content-Type: application/json' \
  -d '{"studentId":1,"courseId":1}'
curl -i -X DELETE http://localhost:8080/enrollments/1
```

자동 실행 기반 검증은 `scripts/test-api.sh`를 사용합니다.
