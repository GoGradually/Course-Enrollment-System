# 저장소 가이드라인

## 프로젝트 구조 및 모듈 구성

* DDD 성격의 레이어를 둔 Java 21 / Spring Boot 3 프로젝트.
* 도메인 로직: `.../domain/**` (schedule, dependency, statistics, member, datetime, converters, events).
* 애플리케이션 서비스: `.../application/**` 에서 유스케이스를 오케스트레이션하며, 얇고 트랜잭션 경계 중심으로 유지.
* 인터페이스: `.../interfaces/web` 컨트롤러 + `.../interfaces/dto` DTO, 공용 헬퍼는 `.../interfaces/utils`.
* 인프라스트럭처: `.../infrastructure/config|repository|web` 에 영속성/어댑터/설정 배치.
* 리소스와 설정은 `src/main/resources` 에 위치(로컬은 `application.yml`). 테스트는 패키지 구조를 동일하게 미러링.

## 빌드, 테스트, 개발 명령어

* `./gradlew clean build` — 컴파일, 테스트 실행, 아티팩트 생성.
* `./gradlew test` — JUnit 5 테스트 스위트 실행(로컬/CI 기본 검증 작업).
* `./gradlew bootRun` — `application.yml` 로컬 설정으로 API 실행.
* `./gradlew bootJar` — 배포 가능한 실행 JAR 생성이 필요할 때 사용.

## 코딩 스타일 및 네이밍 컨벤션

* 4칸 들여쓰기와 표준 Java 컨벤션 사용; 가능한 범위에서 `final`/불변성 선호.
* 레이어 역할이 드러나도록 네이밍(`*Service`, `*Controller`, `*Repository`, `*Response`/`*Request` DTOs).
* 이미 사용 중인 Lombok을 보일러플레이트 제거에 활용하며, 생성자/`@Builder` 사용 패턴은 기존 코드와 일관되게 유지.
* Querydsl은 리포지토리와 함께 두되, 쿼리는 컨트롤러/서비스가 아니라 인프라 레이어에 둔다.
* 문서화 주석은 Javadoc 스타일로 작성하고, 공개 API 메서드에만 적용.
* openapi/Swagger 주석은 컨트롤러 메서드 및 DTO에 추가.
* 도메인 예외 발생 시 예외 클래스 자체로 타입화 (ProductNotFoundException, InvalidStockException), 도메인 레이어가 인터페이스 레이어에 의존하지 않도록 유의

## 테스트 가이드라인

* 프레임워크: JUnit 5, Spring Test, Mockito(JUnit Jupiter 통합 포함).
* 단위 테스트는 도메인/서비스 인접 위치에 두고, 클래스명은 `*Test`, 메서드명은 행위 중심(예: `shouldCreateScheduleWhen...`).
* 도메인 규칙(의존성 사이클, patch 동작)과 리포지토리 쿼리를 커버하고, 외부 경계는 목으로 처리.
* PR 열기 전 `./gradlew test` 실행. 샘플 데이터/설정 추가는 필요한 경우에만.

## 커밋 및 PR 가이드라인

* 히스토리에 사용된 Conventional Commit 스타일 준수(예: `feat: ...`, `docs: ...`, `fix: ...`), 제목은 간결하게.
* PR 설명에는 what/why, 연결된 이슈, 테스트 결과(`./gradlew test` 출력) 포함. 스크린샷은 API 문서/UI 변경에만.
* 스키마/설정 변경(`application.yml`, DB URL/DDL)은 명시하고, 필요 시 마이그레이션/롤백 노트 제공.
* 시크릿 커밋 금지. DB 크리덴셜/외부 키는 환경변수나 프로파일로 오버라이드하고, `application.yml` 은 로컬 개발 기준으로 정상 동작하도록 유지.

## 보안 및 설정 팁

* 로컬 DB 기본값은 `src/main/resources/application.yml` 에 두고, 실제 배포에서는 환경변수/프로파일로 민감값을 오버라이드.
* 로컬이 아닌 환경에서는 `ddl-auto` 를 보수적으로(`validate`/`none`) 유지하고, 스키마 설정 변경 전 팀과 조율.
