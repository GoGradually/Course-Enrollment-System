package me.gogradually.courseenrollmentsystem.interfaces.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.student.StudentQueryService;
import me.gogradually.courseenrollmentsystem.interfaces.dto.StudentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Students")
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentQueryService studentQueryService;

    @Operation(summary = "학생 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "학생 목록 조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<StudentResponse>> getStudents(
            @Parameter(description = "조회 시작 offset", example = "0")
            @RequestParam(required = false) Integer offset,
            @Parameter(description = "조회 건수(limit, 최대 100)", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        List<StudentResponse> students = studentQueryService.getStudents(offset, limit).stream()
                .map(StudentResponse::from)
                .toList();
        return ResponseEntity.ok(students);
    }
}
