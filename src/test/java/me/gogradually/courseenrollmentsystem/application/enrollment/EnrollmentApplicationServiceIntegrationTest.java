package me.gogradually.courseenrollmentsystem.application.enrollment;

import jakarta.persistence.EntityManager;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.course.TimeSlot;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
