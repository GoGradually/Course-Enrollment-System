package me.gogradually.courseenrollmentsystem.interfaces.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.timetable.TimetableQueryService;
import me.gogradually.courseenrollmentsystem.interfaces.dto.TimetableResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Timetable")
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableQueryService timetableQueryService;

    @Operation(summary = "내 시간표 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "시간표 조회 성공"),
            @ApiResponse(responseCode = "404", description = "학생을 찾을 수 없음")
    })
    @GetMapping("/{studentId}/timetable")
    public ResponseEntity<TimetableResponse> getTimetable(@PathVariable Long studentId) {
        TimetableResponse response = TimetableResponse.from(timetableQueryService.getStudentTimetable(studentId));
        return ResponseEntity.ok(response);
    }
}
