package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import me.gogradually.courseenrollmentsystem.application.enrollment.EnrollmentResult;

@Schema(description = "수강신청 응답")
public record EnrollmentResponse(
        @Schema(description = "신청 ID", example = "1001")
        Long enrollmentId,
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @Schema(description = "강좌 ID", example = "101")
        Long courseId,
        @Schema(description = "신청 상태", example = "ACTIVE")
        String status
) {

    public static EnrollmentResponse from(EnrollmentResult result) {
        return new EnrollmentResponse(
                result.enrollmentId(),
                result.studentId(),
                result.courseId(),
                result.status()
        );
    }
}
