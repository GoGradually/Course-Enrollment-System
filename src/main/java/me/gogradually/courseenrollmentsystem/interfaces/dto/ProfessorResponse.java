package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.gogradually.courseenrollmentsystem.application.professor.ProfessorSummary;

@Schema(description = "교수 목록 응답")
public record ProfessorResponse(
        @Schema(description = "교수 ID", example = "10")
        Long id,
        @Schema(description = "교수 이름", example = "김교수")
        String name,
        @Schema(description = "학과 ID", example = "3")
        Long departmentId,
        @Schema(description = "학과명", example = "컴퓨터공학과")
        String departmentName
) {

    public static ProfessorResponse from(ProfessorSummary summary) {
        return new ProfessorResponse(
                summary.id(),
                summary.name(),
                summary.departmentId(),
                summary.departmentName()
        );
    }
}
