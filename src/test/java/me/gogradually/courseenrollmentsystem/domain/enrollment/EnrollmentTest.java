package me.gogradually.courseenrollmentsystem.domain.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.DayOfWeek;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentCancellationNotAllowedException;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.support.DomainFixtureFactory;
import org.junit.jupiter.api.Test;

class EnrollmentTest {

    @Test
    void shouldCancelActiveEnrollment() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course("데이터베이스", 3, 30, 0, DayOfWeek.WEDNESDAY, 9, 10, department, professor);
        Enrollment enrollment = Enrollment.enroll(student, course);

        enrollment.cancel();

        assertEquals(EnrollmentStatus.CANCELED, enrollment.getStatus());
    }

    @Test
    void shouldThrowWhenCancelAlreadyCanceledEnrollment() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course("컴퓨터구조", 3, 30, 0, DayOfWeek.THURSDAY, 9, 10, department, professor);
        Enrollment enrollment = Enrollment.enroll(student, course);
        enrollment.cancel();

        assertThrows(EnrollmentCancellationNotAllowedException.class, enrollment::cancel);
    }
}
