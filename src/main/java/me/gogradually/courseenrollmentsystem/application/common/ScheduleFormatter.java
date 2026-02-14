package me.gogradually.courseenrollmentsystem.application.common;

import java.time.format.DateTimeFormatter;
import me.gogradually.courseenrollmentsystem.domain.course.TimeSlot;

public final class ScheduleFormatter {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private ScheduleFormatter() {
    }

    public static String format(TimeSlot timeSlot) {
        String day = timeSlot.getDayOfWeek().name().substring(0, 3);
        String start = timeSlot.getStartTime().format(TIME_FORMAT);
        String end = timeSlot.getEndTime().format(TIME_FORMAT);
        return day + " " + start + "-" + end;
    }
}
