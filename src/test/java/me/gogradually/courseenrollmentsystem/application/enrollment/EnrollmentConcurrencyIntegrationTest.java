package me.gogradually.courseenrollmentsystem.application.enrollment;

import jakarta.persistence.EntityManager;
import me.gogradually.courseenrollmentsystem.application.enrollment.strategy.EnrollmentStrategyRouter;
import me.gogradually.courseenrollmentsystem.application.enrollment.strategy.EnrollmentStrategyType;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.course.TimeSlot;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentStatus;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EnrollmentConcurrencyIntegrationTest {

    @Autowired
    private EnrollmentStrategyRouter enrollmentStrategyRouter;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @EnumSource(EnrollmentStrategyType.class)
    void shouldAllowOnlyOneSuccessWhenOneSeatAndHundredRequests(EnrollmentStrategyType strategyType) throws InterruptedException {
        SeatRaceFixture fixture = createSeatRaceFixture(1, 100);

        ConcurrentResult result = runSeatRace(fixture.studentIds(), fixture.courseId(), strategyType);

        assertEquals(1, result.successCount());
        assertEquals(99, result.failureCount());
        assertEquals(1, courseRepository.findById(fixture.courseId()).orElseThrow().getEnrolledCount());
        assertEquals(1, countActiveEnrollmentByCourseId(fixture.courseId()));
    }

    @ParameterizedTest
    @EnumSource(EnrollmentStrategyType.class)
    void shouldKeepStudentRuleConsistencyUnderConcurrentRequests(EnrollmentStrategyType strategyType) throws InterruptedException {
        SameStudentFixture fixture = createSameStudentConflictFixture();

        ConcurrentResult result = runSameStudentRace(
                fixture.studentId(),
                fixture.firstCourseId(),
                fixture.secondCourseId(),
                strategyType
        );

        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
        assertEquals(1, enrollmentRepository.findActiveByStudentId(fixture.studentId()).size());

        int enrolledTotal =
                courseRepository.findById(fixture.firstCourseId()).orElseThrow().getEnrolledCount()
                        + courseRepository.findById(fixture.secondCourseId()).orElseThrow().getEnrolledCount();
        assertEquals(1, enrolledTotal);
    }

    private SeatRaceFixture createSeatRaceFixture(int capacity, int studentCount) {
        return executeInTransaction(() -> {
            String token = uniqueToken();

            Department department = new Department("동시성학과-" + token);
            entityManager.persist(department);

            Professor professor = new Professor("동시성교수-" + token, department);
            entityManager.persist(professor);

            Course course = new Course(
                    "CON-" + token,
                    "동시성개론-" + token,
                    3,
                    capacity,
                    0,
                    new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)),
                    department,
                    professor
            );
            entityManager.persist(course);

            List<Student> students = new ArrayList<>();
            for (int index = 0; index < studentCount; index++) {
                Student student = new Student("S" + token + "%03d".formatted(index), "학생-" + token + "-" + index, department);
                entityManager.persist(student);
                students.add(student);
            }

            entityManager.flush();
            entityManager.clear();

            List<Long> studentIds = students.stream()
                    .map(Student::getId)
                    .toList();

            return new SeatRaceFixture(course.getId(), studentIds);
        });
    }

    private SameStudentFixture createSameStudentConflictFixture() {
        return executeInTransaction(() -> {
            String token = uniqueToken();

            Department department = new Department("규칙학과-" + token);
            entityManager.persist(department);

            Professor professor = new Professor("규칙교수-" + token, department);
            entityManager.persist(professor);

            Student student = new Student("R" + token + "001", "동시신청학생-" + token, department);
            entityManager.persist(student);

            Course first = new Course(
                    "RUL-" + token + "-A",
                    "알고리즘-" + token,
                    3,
                    30,
                    0,
                    new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)),
                    department,
                    professor
            );
            entityManager.persist(first);

            Course second = new Course(
                    "RUL-" + token + "-B",
                    "컴퓨터구조-" + token,
                    3,
                    30,
                    0,
                    new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(12, 0)),
                    department,
                    professor
            );
            entityManager.persist(second);

            entityManager.flush();
            entityManager.clear();

            return new SameStudentFixture(student.getId(), first.getId(), second.getId());
        });
    }

    private ConcurrentResult runSeatRace(List<Long> studentIds, Long courseId, EnrollmentStrategyType strategyType)
            throws InterruptedException {
        return runConcurrent(
                studentIds.size(),
                index -> enrollmentStrategyRouter.get(strategyType).enroll(studentIds.get(index), courseId)
        );
    }

    private ConcurrentResult runSameStudentRace(
            Long studentId,
            Long firstCourseId,
            Long secondCourseId,
            EnrollmentStrategyType strategyType
    ) throws InterruptedException {
        List<Long> courseIds = List.of(firstCourseId, secondCourseId);
        return runConcurrent(
                2,
                index -> enrollmentStrategyRouter.get(strategyType).enroll(studentId, courseIds.get(index))
        );
    }

    private ConcurrentResult runConcurrent(int taskCount, IntThrowingRunnable task) throws InterruptedException {
        int threadCount = taskCount;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(taskCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(taskCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failure = new AtomicInteger();

        try {
            for (int index = 0; index < taskCount; index++) {
                final int taskIndex = index;
                executorService.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        task.run(taskIndex);
                        success.incrementAndGet();
                    } catch (Exception exception) {
                        failure.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS));
            return new ConcurrentResult(success.get(), failure.get());
        } finally {
            executorService.shutdownNow();
        }
    }

    private long countActiveEnrollmentByCourseId(Long courseId) {
        return entityManager.createQuery(
                        """
                                select count(e)
                                from Enrollment e
                                where e.course.id = :courseId
                                  and e.status = :status
                                """,
                        Long.class
                )
                .setParameter("courseId", courseId)
                .setParameter("status", EnrollmentStatus.ACTIVE)
                .getSingleResult();
    }

    private String uniqueToken() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private <T> T executeInTransaction(TransactionWork<T> work) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        T result = transactionTemplate.execute(status -> work.run());
        if (result == null) {
            throw new IllegalStateException("Transaction callback returned null");
        }
        return result;
    }

    @FunctionalInterface
    private interface IntThrowingRunnable {
        void run(int index) throws Exception;
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run();
    }

    private record SeatRaceFixture(Long courseId, List<Long> studentIds) {
    }

    private record SameStudentFixture(Long studentId, Long firstCourseId, Long secondCourseId) {
    }

    private record ConcurrentResult(int successCount, int failureCount) {
    }
}
