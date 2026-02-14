package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.gogradually.courseenrollmentsystem.application.student.StudentSummary;

@Schema(description = "학생 목록 응답")
public record StudentResponse(
        @Schema(description = "학생 ID", example = "1")
        Long id,
        @Schema(description = "학번", example = "20260001")
        String studentNumber,
        @Schema(description = "학생 이름", example = "홍길동")
        String name,
        @Schema(description = "학과 ID", example = "3")
        Long departmentId,
        @Schema(description = "학과명", example = "컴퓨터공학과")
        String departmentName
) {

    public static StudentResponse from(StudentSummary summary) {
        return new StudentResponse(
                summary.id(),
                summary.studentNumber(),
                summary.name(),
                summary.departmentId(),
                summary.departmentName()
        );
    }
}
