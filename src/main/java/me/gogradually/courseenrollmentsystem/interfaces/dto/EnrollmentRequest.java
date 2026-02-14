package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "수강신청 요청")
public record EnrollmentRequest(
        @NotNull(message = "studentId is required")
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @NotNull(message = "courseId is required")
        @Schema(description = "강좌 ID", example = "101")
        Long courseId
) {
}
