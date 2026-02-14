package me.gogradually.courseenrollmentsystem.domain.course;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseCapacityExceededException;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.support.DomainFixtureFactory;
import org.junit.jupiter.api.Test;

class CourseTest {

    @Test
    void shouldIncreaseEnrollmentWhenCapacityAvailable() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Course course = DomainFixtureFactory.course("자료구조", 3, 30, 29, DayOfWeek.MONDAY, 9, 10, department, professor);

        assertDoesNotThrow(course::increaseEnrollment);
        assertEquals(30, course.getEnrolledCount());
    }

    @Test
    void shouldThrowWhenCapacityExceeded() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Course course = DomainFixtureFactory.course("운영체제", 3, 30, 30, DayOfWeek.MONDAY, 10, 11, department, professor);

        assertThrows(CourseCapacityExceededException.class, course::increaseEnrollment);
    }

    @Test
    void shouldDetectScheduleConflictWhenTimeOverlaps() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Course base = DomainFixtureFactory.course("알고리즘", 3, 30, 0, DayOfWeek.TUESDAY, 9, 11, department, professor);
        Course overlap = DomainFixtureFactory.course("네트워크", 3, 30, 0, DayOfWeek.TUESDAY, 10, 12, department, professor);

        assertTrue(base.hasScheduleConflictWith(overlap));
    }
}
