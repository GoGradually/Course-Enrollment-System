package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.gogradually.courseenrollmentsystem.application.timetable.TimetableCourseSummary;

@Schema(description = "시간표 내 강좌 정보")
public record TimetableCourseResponse(
        @Schema(description = "강좌 ID", example = "101")
        Long courseId,
        @Schema(description = "강좌명", example = "자료구조")
        String courseName,
        @Schema(description = "학점", example = "3")
        int credits,
        @Schema(description = "강의 시간", example = "MON 09:00-10:30")
        String schedule,
        @Schema(description = "교수명", example = "김교수")
        String professorName,
        @Schema(description = "학과명", example = "컴퓨터공학과")
        String departmentName
) {

    public static TimetableCourseResponse from(TimetableCourseSummary summary) {
        return new TimetableCourseResponse(
                summary.courseId(),
                summary.courseName(),
                summary.credits(),
                summary.schedule(),
                summary.professorName(),
                summary.departmentName()
        );
    }
}
