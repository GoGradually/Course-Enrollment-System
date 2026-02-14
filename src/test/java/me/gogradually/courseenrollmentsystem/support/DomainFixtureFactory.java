package me.gogradually.courseenrollmentsystem.support;

import java.time.DayOfWeek;
import java.time.LocalTime;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.TimeSlot;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;

public final class DomainFixtureFactory {

    private DomainFixtureFactory() {
    }

    public static Department department() {
        return new Department("컴퓨터공학과");
    }

    public static Professor professor(Department department) {
        return new Professor("김교수", department);
    }

    public static Student student(Department department) {
        return new Student("20260001", "홍길동", department);
    }

    public static Course course(
        String code,
        int credits,
        int capacity,
        int enrolledCount,
        DayOfWeek dayOfWeek,
        int startHour,
        int endHour,
        Department department,
        Professor professor
    ) {
        return new Course(
            code,
            code,
            credits,
            capacity,
            enrolledCount,
            new TimeSlot(dayOfWeek, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0)),
            department,
            professor
        );
    }
}
