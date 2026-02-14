package me.gogradually.courseenrollmentsystem.interfaces.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.professor.ProfessorQueryService;
import me.gogradually.courseenrollmentsystem.interfaces.dto.ProfessorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Professors")
@RestController
@RequestMapping("/professors")
@RequiredArgsConstructor
public class ProfessorController {

    private final ProfessorQueryService professorQueryService;

    @Operation(summary = "교수 목록 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "교수 목록 조회 성공")
    })
    @GetMapping
    public ResponseEntity<List<ProfessorResponse>> getProfessors(
            @Parameter(description = "조회 시작 offset", example = "0")
            @RequestParam(required = false) Integer offset,
            @Parameter(description = "조회 건수(limit, 최대 100)", example = "50")
            @RequestParam(required = false) Integer limit
    ) {
        List<ProfessorResponse> professors = professorQueryService.getProfessors(offset, limit).stream()
                .map(ProfessorResponse::from)
                .toList();
        return ResponseEntity.ok(professors);
    }
}
