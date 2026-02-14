package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 에러 응답")
public record ErrorResponse(
        @Schema(description = "에러 코드", example = "COURSE_NOT_FOUND")
        String code,
        @Schema(description = "에러 메시지", example = "Course not found. courseId=999")
        String message,
        @Schema(description = "에러 발생 시각", example = "2026-02-08T16:30:00+09:00")
        String timestamp
) {
}
