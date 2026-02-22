# 대학교 수강신청 시스템 요구사항 결정 기록

## AI 활용 방식

1. 요구사항 문서 분석 및 체크리스트 분해
2. 도메인 규칙/예외 정책 설계
3. API 설계 및 테스트 케이스 도출
4. 동시성 제어 전략 비교(비관적/낙관적/원자적)
5. 구현 후 코드-문서 동기화

## 범위 및 기본 정책

- 학기 모델: 단일 학기 고정
- 수강 상태: `ACTIVE`, `CANCELED`만 사용
- 수강취소 재호출: 멱등 성공이 아니라 명시적 예외 반환
- 인증/인가: 과제 범위 밖으로 두고 ID 기반 요청 허용
- 재수강/선수과목/학년 제한: 기본 미적용

---

## 인프라 구성

- WAS: Spring Boot 내장 Tomcat
- DB: H2 In-Memory
- 트랜잭션: Spring Transaction + JPA
- API 문서화: Swagger(OpenAPI Annotation)

---

## 초기 데이터 생성 전략

- 애플리케이션 시작 시 `ApplicationRunner`에서 동적으로 시드 데이터를 생성
- 정적 SQL/CSV 없이 코드 조합 로직으로 생성
- 고정 랜덤 시드(`app.seed.random-seed`) 사용으로 재현 가능한 데이터 세트 유지
- 기본 생성 규모(`app.seed.*`)
  - 학과 12
  - 교수 120
  - 학생 10,000
  - 강좌 600
- 현실성 규칙
  - 학과명: 한국 대학 맥락의 실제 학과명 토큰 사용
  - 학생/교수명: 성씨 + 이름 토큰 조합
  - 강좌명: 전공 주제 + 개론/이론/응용/실습 등 접미 조합
  - 금지 패턴: `User1`, `Course1` 형태 미사용
- 정합성 규칙
  - 학생 학번, 강좌 코드 유니크 보장
  - 초기 `enrolledCount`는 0으로 시작
  - 초기 Enrollment 데이터는 생성하지 않음
- 성능 규칙
  - `batch-size` 기준 flush/clear로 초기화 메모리 사용량 제어
  - 부팅 로그에 초기화 소요 시간(ms) 기록
  - 실측 기준(2026-02-08)
    - `Started CourseEnrollmentSystemApplication in 2.418 seconds`
    - `Initial data generation completed ... elapsedMs=625`
    - `GET /health` `200`, `time_total=0.068219s`
    - `GET /students?limit=5` `200`, `time_total=0.035376s`
    - `GET /courses?limit=5` `200`, `time_total=0.011840s`
    - `GET /professors?limit=5` `200`, `time_total=0.005930s`

---

## 비즈니스 규칙

### Student

- 최대 신청 학점 18학점
- 인증/인가 제외, ID 기반 요청 허용

### Course

- 정원(capacity) 고정
- 현재 신청 인원(enrolledCount) 관리
- 시간표(schedule): 요일 + 시작시간 + 종료시간

### Enrollment

- 동일 강좌 중복 신청 금지
- 한 학생의 동일 시간대 강의 중복 신청 금지(요일 + 시간 겹침)
  - 수강신청 시 학생 Lock 획득으로 일관성 보장
- 최대 학점 초과 신청 금지
  - 수강신청 시 학생 Lock 획득으로 일관성 보장
- 강좌 정원 초과 신청 금지

---

## 동시성 제어 전략

### 공통 원칙

- 수강신청 트랜잭션 격리 수준은 프로파일로 전환 가능
  - `read-committed` -> `TRANSACTION_READ_COMMITTED`
  - `repeatable-read` -> `TRANSACTION_REPEATABLE_READ`
- 성능 비교/검증을 위해 전략별 API를 분리
  - `POST /enrollments` (기본)
  - `POST /enrollments/pessimistic`
  - `POST /enrollments/optimistic`
  - `POST /enrollments/atomic`
  - `POST /enrollments/separated`

### 기본 전략: Atomic Update(락 범위 최소화)

- `enrollments`는 FK 제약을 두지 않고 인덱스로 조회 성능을 보강
  - `idx_enrollments_student_status(student_id, status)`
  - `idx_enrollments_course_status(course_id, status)`
- `POST /enrollments` 기본 경로는 먼저 활성 신청 row를 삽입해 중복 신청을 유니크 제약으로 차단
- 다음으로 강좌 정원을 업데이트 락으로 선점
  - `UPDATE courses SET enrolled_count = enrolled_count + 1 WHERE id = :courseId AND enrolled_count < capacity`
  - 좌석 업데이트 영향 행수(`affectedRows`)가 `0`이면 만석 예외 반환
- 좌석 선점 후 동일 트랜잭션 내에서
  - 강좌/학생 조회 시점에 미존재면 `404` 예외 반환
  - 학생 락 획득(`PESSIMISTIC_WRITE`)(**Materialized Conflict - 한 학생이 두개의 코스 동시 신청 차단**)
  - 학점/시간표 규칙 검증
- 중간 단계 실패 시 트랜잭션 롤백으로 신청 insert/정원 update를 함께 취소

> 추가로 가능하다면 UPDATE와 INSERT를 별도 트랜잭션으로 분리
> - UPDATE 트랜잭션: 정원 경쟁 처리
> - INSERT 트랜잭션: 중복 신청만 방지
> - 검증결과 실패 시, 보상 트랜잭션 구현 -> 실제 수치로 어느정도 이득인지 판단 필요

### 비관적 락 전략

- 강좌를 `PESSIMISTIC_WRITE`로 조회(`SELECT ... FOR UPDATE`)
- 정원 검증 후 `enrolledCount` 증가
- 신청 저장

### 낙관적 락 전략

- `Course` 엔티티 `@Version` 필드 사용
- 충돌 시 최대 3회 재시도
- 재시도 소진 시 `EnrollmentConcurrencyConflictException` 반환(HTTP 409)

### 동일 학생 동시 신청(D2)

- 학생 엔티티를 `PESSIMISTIC_WRITE`로 조회하여 학생 단위 직렬화
- 동시 요청 경합에서도 학점/시간표/중복 규칙 일관성 유지

---

## 동시성 테스트 전략

- 프레임워크: JUnit 5 + Spring Boot Test
- 통합 테스트 클래스: `EnrollmentConcurrencyIntegrationTest`
- 검증 시나리오
  - 정원 1, 동시 100요청(전략별) -> 성공 1 / 실패 99
  - 동일 학생 동시 신청(전략별) -> 학점/시간표 규칙 위반 상태 불가
- 검증 기준
  - 최종 `enrolled_count <= capacity` 항상 만족
  - 활성 신청 데이터가 비즈니스 규칙을 위반하지 않음

## 트레이드오프

- Atomic Update 기본 전략은 정원 경쟁을 짧은 DB 연산으로 처리해 고경합에서 유리하지만, 후속 규칙 검증 실패 시 롤백 비용이 발생
- 비관적 락은 구현이 직관적이고 정합성이 강하지만 경합 시 대기 시간이 늘어날 수 있음
- 낙관적 락은 충돌이 낮을 때 유리하지만, 고경합 구간에서는 재시도 비용이 증가

---
