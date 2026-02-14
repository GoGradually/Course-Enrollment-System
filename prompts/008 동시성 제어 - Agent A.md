현재 "프로젝트 요구사항 구성.md" 파일에 프로젝트에 대한 요구사항이 정리되어 있다.

또한 IMPLEMENTATION_CHECKLIST.md 파일 내에 이 프로젝트를 구현하기 위한 상세 체크리스트가 작성되어 있다.

---

이를 바탕으로, "D. 동시성 제어"를 완성하라.

---

또한, 완성된 요구사항은 IMPLEMENTATION_CHECKLIST.md 파일 내 체크박스에 반영되어야 한다.

---

- 학기 모델은 B 범위에서 단일 학기로 고정합니다.
- 수강신청 상태는 최소 ACTIVE, CANCELED만 사용합니다.
- 취소 정책은 멱등 성공이 아니라 명시적 예외를 기본값으로 사용합니다.
- 동시성 고도화(100명 경쟁)는 D 섹션에서 확장하며, B 단계에서는 규칙 정확성과 예외 타입화를 우선했습니다.

---

기본 동작은 update 쿼리로 먼저 자리를 하나 빼보고, 영향받은 컬럼이 0이 아닐 때(업데이트가 성공했을 때)만 실제로 동작을 수행하는 락 범위 최소화 형태로 해줘.

```sql
update Course c
set c.enrolledCount = c.enrolledCount + 1
where c.id = :courseId
  and c.enrolledCount < c.capacity
-- affected rows = 1 이면 좌석 확보 성공
```

```sql
insert into Enrollment (student_id, course_id, status, created_at)
values (:studentId, :courseId, 'ACTIVE', now())

// 이미 수강신청한 경우 unique 제약조건 위반 예외 발생
```

---


현재 사용하는 atomic update가, 트랜잭션 길이를 짧게 하기 위해 JPA기능을 사용하지 않고, enroll에 unique update 를 걸고 DB에 일단 삽입을 시도하는 형태로 동작했으면 좋겠어.


