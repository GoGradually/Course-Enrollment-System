package me.gogradually.courseenrollmentsystem.application.timetable;

import me.gogradually.courseenrollmentsystem.application.common.ScheduleFormatter;
import me.gogradually.courseenrollmentsystem.domain.course.Course;

public record TimetableCourseSummary(
    Long courseId,
    String courseName,
    int credits,
    String schedule,
    String professorName,
    String departmentName
) {

    public static TimetableCourseSummary from(Course course) {
        return new TimetableCourseSummary(
            course.getId(),
            course.getName(),
            course.getCredits(),
            ScheduleFormatter.format(course.getTimeSlot()),
            course.getProfessor().getName(),
            course.getDepartment().getName()
        );
    }
}
