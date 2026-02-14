package me.gogradually.courseenrollmentsystem.application.course;

import me.gogradually.courseenrollmentsystem.application.common.ScheduleFormatter;
import me.gogradually.courseenrollmentsystem.domain.course.Course;

public record CourseSummary(
    Long id,
    String courseCode,
    String name,
    int credits,
    int capacity,
    int enrolled,
    String schedule,
    Long departmentId,
    String departmentName,
    Long professorId,
    String professorName
) {

    public static CourseSummary from(Course course) {
        return new CourseSummary(
            course.getId(),
            course.getCourseCode(),
            course.getName(),
            course.getCredits(),
            course.getCapacity(),
            course.getEnrolledCount(),
            ScheduleFormatter.format(course.getTimeSlot()),
            course.getDepartment().getId(),
            course.getDepartment().getName(),
            course.getProfessor().getId(),
            course.getProfessor().getName()
        );
    }
}
