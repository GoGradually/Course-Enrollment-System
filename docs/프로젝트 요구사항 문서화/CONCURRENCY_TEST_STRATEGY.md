# 동시성 테스트 전략 구성 (Fast + Repeatable)

## 1. 요약
대학교 수강신청 시스템의 핵심 요구사항(정원 초과 방지, 동일 학생 경합 규칙)을 빠르고 반복 가능하게 검증하기 위해, `test` 프로필 전용 테스트 지원 API와 서비스 레벨 동시성 테스트를 결합한다.
운영 API는 순수하게 유지하고, 테스트 실행 시에만 데이터 리셋/시나리오 준비 기능을 노출해 재현성과 속도를 동시에 확보한다.

## 2. 목표 및 성공 기준
### 목표
- 정원 1 강좌에 동시 100 요청 시 성공 1건, 실패 99건 보장
- 최종 `enrolled <= capacity` 항상 만족
- 동일 학생 동시 신청에서도 학점(`<=18`), 시간충돌, 중복신청 불변식 유지

### 성공 기준
- 동시성 테스트가 반복 실행되어도 동일 결과를 반환
- 테스트 수행 시간이 과도하게 길지 않음(서비스 레벨 동시 테스트 중심)
- 실패 원인(정원, 학점, 시간충돌, 중복, 락 충돌)이 에러 코드로 구분됨

## 3. 범위
### 포함
- 동시성 테스트 친화적 API 설계(`test` 프로필 한정)
- 서비스 계층 동시성 테스트 구조
- 시드 기반 초기 데이터 재생성 전략
- 문서화(`docs/REQUIREMENTS.md`, API 명세)

### 제외
- 운영 환경에서 테스트 보조 API 노출
- 대규모 성능 튜닝(정확성/재현성 우선)

## 4. API/인터페이스 변경 사항
### 운영 API
- `POST /enrollments`
  - 요청: `studentId`, `courseId`
  - 성공: enrollment 식별 정보 반환
  - 실패: `409`(정원초과/중복/시간충돌), `422`(학점초과) 등
- `DELETE /enrollments/{enrollmentId}`
- `GET /students`
- `GET /courses`
- `GET /professors`
- `GET /students/{studentId}/timetable`

### 테스트 전용 API(`@Profile("test")`)
- `POST /test-support/reset`
  - 목적: 시드 기반 데이터 재생성(결정적 초기화)
  - 요청: `scenarioKey`(예: `SEAT_RACE_CAPACITY_1`), `seed`(optional)
  - 응답: 테스트에 필요한 핵심 ID 목록
- `POST /test-support/scenarios/seat-race`
  - 목적: 정원 경쟁용 데이터 사전 구성
  - 요청: `capacity`, `contenders`
  - 응답: `courseId`, `studentIds`

### 노출 제어
- 테스트 API는 `test` 프로필에서만 등록
- 운영 OpenAPI 문서와 분리(혼선 방지)

## 5. 내부 설계 (DDD)
- `interfaces/web`
  - 운영 컨트롤러와 테스트 지원 컨트롤러 분리
- `application`
  - `EnrollmentCommandService`(운영 유스케이스)
  - `TestScenarioService`(테스트 데이터 리셋/준비)
- `domain`
  - 정원/학점/시간충돌/중복신청 불변식 캡슐화
  - 타입화된 도메인 예외 유지
- `infrastructure/repository`
  - 동시성 전략 구현(락 또는 원자적 업데이트 + 재검증)
  - 테스트 데이터 생성 최적화

## 6. 동시성 검증 전략
### 6.1 서비스 레벨 동시 테스트(주력)
- `CountDownLatch`로 동시 시작 강제
- 고정 스레드풀 사용
- 시나리오
  - 정원 1 / 요청 100
  - 정원 30 / 요청 100
  - 동일 학생의 충돌 시간대 동시 신청
  - 동일 학생의 18학점 경계 동시 신청

### 6.2 HTTP 통합 테스트(보조)
- 핵심 1~2개 시나리오만 병렬 호출
- 직렬화/예외 매핑/응답코드 정합성 확인 목적

## 7. 테스트 케이스 목록
- `shouldAllowOnlyOneEnrollmentWhen100ConcurrentRequestsOnCapacityOneCourse`
- `shouldNeverExceedCourseCapacityUnderConcurrentRequests`
- `shouldPreventDuplicateEnrollmentForSameStudentAndCourseUnderRace`
- `shouldKeepStudentCreditsAtMost18UnderConcurrentEnrollments`
- `shouldRejectTimetableConflictUnderConcurrentEnrollments`
- `shouldResetScenarioDeterministicallyWithSameSeed`

### 공통 검증 포인트
- 성공/실패 건수
- 최종 상태(`enrolled`, 학생 총학점, 중복 enrollment 여부)
- 응답/예외 코드 일치성

## 8. CI/실행 규칙
- 기본: `./gradlew test`
- 동시성 전용 태깅으로 빠른 스위트/확장 스위트 분리 권장
- flaky 방지
  - 고정 seed 사용
  - 타임아웃 명시
  - 스레드 수/요청 수 상수화

## 9. 문서화 반영
- `docs/REQUIREMENTS.md`
  - 동시성 제어 전략, 트랜잭션 경계, 트레이드오프
  - 실패/재시도 정책
  - 재현 방법(seed, reset API)
- API 문서
  - 운영 API와 테스트 API 섹션 분리
  - 테스트 API는 `test` 프로필 조건 명시

## 10. 가정 및 기본값
- 단일 학기 모델 사용
- 테스트 보조 API는 운영 비노출(`test` 프로필 전용)
- 동시성 검증 중심은 서비스 레벨 테스트
- 데이터 초기화는 시드 기반 재생성 + 리셋 API
- 공통 에러 응답 포맷(`code`, `message`, `timestamp`) 사용
