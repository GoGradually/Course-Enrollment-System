package me.gogradually.courseenrollmentsystem.interfaces.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import me.gogradually.courseenrollmentsystem.application.timetable.StudentTimetable;

import java.util.List;

@Schema(description = "학생 시간표 응답")
public record TimetableResponse(
        @Schema(description = "학생 ID", example = "1")
        Long studentId,
        @Schema(description = "현재 총 신청 학점", example = "9")
        int totalCredits,
        @ArraySchema(schema = @Schema(implementation = TimetableCourseResponse.class))
        List<TimetableCourseResponse> courses
) {

    public static TimetableResponse from(StudentTimetable timetable) {
        List<TimetableCourseResponse> courseResponses = timetable.courses().stream()
                .map(TimetableCourseResponse::from)
                .toList();
        return new TimetableResponse(timetable.studentId(), timetable.totalCredits(), courseResponses);
    }
}
