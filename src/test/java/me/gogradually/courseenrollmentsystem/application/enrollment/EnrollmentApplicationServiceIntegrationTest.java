package me.gogradually.courseenrollmentsystem.application.enrollment;

import jakarta.persistence.EntityManager;
import me.gogradually.courseenrollmentsystem.application.enrollment.orchestration.EnrollmentApplicationService;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.course.TimeSlot;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentStatus;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.exception.ScheduleConflictException;
import me.gogradually.courseenrollmentsystem.domain.exception.StudentNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class EnrollmentApplicationServiceIntegrationTest {

    @Autowired
    private EnrollmentApplicationService enrollmentApplicationService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldThrowStudentNotFoundWhenAtomicEnrollmentRequestedWithUnknownStudent() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        StudentMissingFixture fixture = transactionTemplate.execute(status -> {
            Department department = new Department("기계공학과");
            entityManager.persist(department);

            Professor professor = new Professor("정교수", department);
            entityManager.persist(professor);

            Course course = new Course(
                    "ME101",
                    "열역학",
                    3,
                    20,
                    0,
                    new TimeSlot(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(10, 30)),
                    department,
                    professor
            );
            entityManager.persist(course);
            entityManager.flush();
            entityManager.clear();
            return new StudentMissingFixture(course.getId());
        });

        assertNotNull(fixture);

        assertThrows(
                StudentNotFoundException.class,
                () -> enrollmentApplicationService.enroll(999_999L, fixture.courseId())
        );

        transactionTemplate.executeWithoutResult(status -> {
            assertEquals(0, countActiveEnrollmentByCourseId(fixture.courseId()));
            assertEquals(0, courseRepository.findById(fixture.courseId()).orElseThrow().getEnrolledCount());
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldThrowCourseNotFoundWhenAtomicEnrollmentRequestedWithUnknownCourse() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CourseMissingFixture fixture = transactionTemplate.execute(status -> {
            Department department = new Department("산업공학과");
            entityManager.persist(department);

            Student student = new Student("20261236", "최학생", department);
            entityManager.persist(student);
            entityManager.flush();
            entityManager.clear();
            return new CourseMissingFixture(student.getId());
        });

        assertNotNull(fixture);

        assertThrows(
                CourseNotFoundException.class,
                () -> enrollmentApplicationService.enroll(fixture.studentId(), 999_999L)
        );

        transactionTemplate.executeWithoutResult(status ->
                assertEquals(0, enrollmentRepository.findActiveByStudentId(fixture.studentId()).size())
        );
    }

    @Test
    void shouldAllowReEnrollmentAfterCancellation() {
        Department department = new Department("전자공학과");
        entityManager.persist(department);

        Professor professor = new Professor("박교수", department);
        entityManager.persist(professor);

        Student student = new Student("20261234", "김학생", department);
        entityManager.persist(student);

        Course course = new Course(
            "EE101",
            "회로이론",
            3,
            10,
            0,
            new TimeSlot(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0)),
            department,
            professor
        );
        entityManager.persist(course);
        entityManager.flush();
        entityManager.clear();

        Enrollment first = enrollmentApplicationService.enroll(student.getId(), course.getId());
        enrollmentApplicationService.cancel(first.getId());

        List<Enrollment> afterCancel = enrollmentRepository.findActiveByStudentId(student.getId());
        assertEquals(0, afterCancel.size());

        int creditsAfterCancel = afterCancel.stream()
            .map(Enrollment::getCourse)
            .mapToInt(Course::getCredits)
            .sum();
        assertEquals(0, creditsAfterCancel);

        Enrollment second = enrollmentApplicationService.enroll(student.getId(), course.getId());

        assertNotNull(second.getId());
        List<Enrollment> activeEnrollments = enrollmentRepository.findActiveByStudentId(student.getId());
        assertEquals(1, activeEnrollments.size());

        int currentCredits = activeEnrollments.stream()
            .map(Enrollment::getCourse)
            .mapToInt(Course::getCredits)
            .sum();

        entityManager.flush();
        entityManager.clear();

        assertEquals(3, currentCredits);
        assertEquals(1, courseRepository.findById(course.getId()).orElseThrow().getEnrolledCount());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldAllowSeparatedReEnrollmentAfterCancellationWhenHistoryExists() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        SeparatedReEnrollmentFixture fixture = transactionTemplate.execute(status -> {
            Department department = new Department("소프트웨어학과");
            entityManager.persist(department);

            Professor professor = new Professor("강교수", department);
            entityManager.persist(professor);

            Student student = new Student("20261237", "장학생", department);
            entityManager.persist(student);

            Course course = new Course(
                    "SWE101",
                    "소프트웨어공학",
                    3,
                    20,
                    0,
                    new TimeSlot(DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(15, 30)),
                    department,
                    professor
            );
            entityManager.persist(course);
            entityManager.flush();
            entityManager.clear();
            return new SeparatedReEnrollmentFixture(student.getId(), course.getId());
        });

        assertNotNull(fixture);

        Enrollment first = enrollmentApplicationService.enrollWithSeparatedTransaction(
                fixture.studentId(),
                fixture.courseId()
        );
        enrollmentApplicationService.cancel(first.getId());

        Enrollment second = enrollmentApplicationService.enrollWithSeparatedTransaction(
                fixture.studentId(),
                fixture.courseId()
        );

        assertNotNull(second.getId());
        assertEquals(1, courseRepository.findById(fixture.courseId()).orElseThrow().getEnrolledCount());
        assertEquals(
                1,
                enrollmentRepository.findActiveByStudentId(fixture.studentId()).stream()
                        .filter(enrollment -> enrollment.getCourse().getId().equals(fixture.courseId()))
                        .count()
        );
        assertEquals(
                1,
                countEnrollmentByStudentAndCourseAndStatus(
                        fixture.studentId(),
                        fixture.courseId(),
                        EnrollmentStatus.CANCELED
                )
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void shouldReleaseReservedSeatWhenSeparatedTransactionValidationFails() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Fixture fixture = transactionTemplate.execute(status -> {
            Department department = new Department("컴퓨터공학과");
            entityManager.persist(department);

            Professor professor = new Professor("김교수", department);
            entityManager.persist(professor);

            Student student = new Student("20261235", "이학생", department);
            entityManager.persist(student);

            Course existingCourse = new Course(
                    "CSE-FIX-201",
                    "자료구조",
                    3,
                    30,
                    0,
                    new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(11, 30)),
                    department,
                    professor
            );
            entityManager.persist(existingCourse);

            Course requestedCourse = new Course(
                    "CSE-FIX-202",
                    "알고리즘",
                    3,
                    30,
                    0,
                    new TimeSlot(DayOfWeek.TUESDAY, LocalTime.of(11, 0), LocalTime.of(12, 0)),
                    department,
                    professor
            );
            entityManager.persist(requestedCourse);
            entityManager.flush();
            entityManager.clear();

            return new Fixture(student.getId(), existingCourse.getId(), requestedCourse.getId());
        });

        assertNotNull(fixture);
        enrollmentApplicationService.enroll(fixture.studentId(), fixture.existingCourseId());

        assertThrows(
                ScheduleConflictException.class,
                () -> enrollmentApplicationService.enrollWithSeparatedTransaction(
                        fixture.studentId(),
                        fixture.requestedCourseId()
                )
        );

        assertEquals(0, courseRepository.findById(fixture.requestedCourseId()).orElseThrow().getEnrolledCount());
        assertEquals(
                0,
                enrollmentRepository.findActiveByStudentId(fixture.studentId()).stream()
                        .filter(enrollment -> enrollment.getCourse().getId().equals(fixture.requestedCourseId()))
                        .count()
        );
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

    private long countEnrollmentByStudentAndCourseAndStatus(Long studentId, Long courseId, EnrollmentStatus status) {
        return entityManager.createQuery(
                        """
                                select count(e)
                                from Enrollment e
                                where e.student.id = :studentId
                                  and e.course.id = :courseId
                                  and e.status = :status
                                """,
                        Long.class
                )
                .setParameter("studentId", studentId)
                .setParameter("courseId", courseId)
                .setParameter("status", status)
                .getSingleResult();
    }

    private record Fixture(Long studentId, Long existingCourseId, Long requestedCourseId) {
    }

    private record SeparatedReEnrollmentFixture(Long studentId, Long courseId) {
    }

    private record StudentMissingFixture(Long courseId) {
    }

    private record CourseMissingFixture(Long studentId) {
    }
}
