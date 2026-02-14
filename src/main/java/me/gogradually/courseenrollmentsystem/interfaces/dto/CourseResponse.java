package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.gogradually.courseenrollmentsystem.application.course.CourseSummary;

@Schema(description = "강좌 목록 응답")
public record CourseResponse(
        @Schema(description = "강좌 ID", example = "101")
        Long id,
        @Schema(description = "강좌 코드", example = "CSE301")
        String courseCode,
        @Schema(description = "강좌명", example = "자료구조")
        String name,
        @Schema(description = "학점", example = "3")
        int credits,
        @Schema(description = "정원", example = "30")
        int capacity,
        @Schema(description = "현재 신청 인원", example = "25")
        int enrolled,
        @Schema(description = "강의 시간", example = "MON 09:00-10:30")
        String schedule,
        @Schema(description = "학과 ID", example = "3")
        Long departmentId,
        @Schema(description = "학과명", example = "컴퓨터공학과")
        String departmentName,
        @Schema(description = "교수 ID", example = "12")
        Long professorId,
        @Schema(description = "교수명", example = "김교수")
        String professorName
) {

    public static CourseResponse from(CourseSummary summary) {
        return new CourseResponse(
                summary.id(),
                summary.courseCode(),
                summary.name(),
                summary.credits(),
                summary.capacity(),
                summary.enrolled(),
                summary.schedule(),
                summary.departmentId(),
                summary.departmentName(),
                summary.professorId(),
                summary.professorName()
        );
    }
}
