package me.gogradually.courseenrollmentsystem.application.enrollment;

import jakarta.persistence.EntityManager;
import me.gogradually.courseenrollmentsystem.application.enrollment.orchestration.EnrollmentApplicationService;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.course.TimeSlot;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.ScheduleConflictException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private record Fixture(Long studentId, Long existingCourseId, Long requestedCourseId) {
    }
}
