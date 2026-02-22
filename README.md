# University Enrollment API

대학교 수강신청 시스템 백엔드 과제 구현입니다.

## 1. 기술 스택

- Java 21
- Spring Boot 3.3.5
- Spring Data JPA
- MySQL 8.x
- SpringDoc OpenAPI (Swagger UI)

## 2. 실행 전 요구사항

- JDK 21
- MySQL (localhost:3307)
- 데이터베이스: `course_enrollment`
- (권장) `curl`, `jq`

## 3. 빌드 및 테스트

```bash
./gradlew clean build
```

```bash
./gradlew test
```

## 4. 서버 실행

```bash
./gradlew bootRun
```

기본 접속 정보:

- Base URL: `http://localhost:8080`
- Health Check: `GET /health`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## 5. API 문서

- 상세 명세: `docs/API_SPEC.md`
- open-api.yaml: `docs/내 API 문서.yaml`
- 요구사항/설계 결정: `docs/REQUIREMENTS.md`

## 6. 테스트용 curl 스크립트

실행 전에 서버(`./gradlew bootRun`)가 떠 있어야 합니다.

```bash
chmod +x scripts/test-api.sh
./scripts/test-api.sh
```

환경변수로 동작을 조정할 수 있습니다:

- `BASE_URL` (기본: `http://localhost:8080`)
- `ENROLL_STRATEGY` (기본: `atomic`, 지원: `default|pessimistic|optimistic|atomic|separated`)
- `PARALLEL_REQUESTS` (기본: `20`)

예시:

```bash
ENROLL_STRATEGY=pessimistic PARALLEL_REQUESTS=50 ./scripts/test-api.sh
```
