package me.gogradually.courseenrollmentsystem.application.timetable;

import java.util.List;

public record StudentTimetable(
    Long studentId,
    int totalCredits,
    List<TimetableCourseSummary> courses
) {
}
