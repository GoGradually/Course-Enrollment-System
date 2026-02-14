package me.gogradually.courseenrollmentsystem.interfaces.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.course.CourseQueryService;
import me.gogradually.courseenrollmentsystem.interfaces.dto.CourseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Courses")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseQueryService courseQueryService;

    @Operation(summary = "강좌 목록 조회", description = "전체 또는 학과별 강좌 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "강좌 목록 조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<CourseResponse>> getCourses(
            @Parameter(description = "학과 ID 필터", example = "3")
            @RequestParam(required = false) Long departmentId,
            @Parameter(description = "조회 시작 offset", example = "0")
            @RequestParam(required = false) Integer offset,
            @Parameter(description = "조회 건수(limit, 최대 100)", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        List<CourseResponse> courses = courseQueryService.getCourses(departmentId, offset, limit).stream()
                .map(CourseResponse::from)
                .toList();
        return ResponseEntity.ok(courses);
    }
}
