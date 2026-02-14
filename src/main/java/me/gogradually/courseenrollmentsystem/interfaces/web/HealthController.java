package me.gogradually.courseenrollmentsystem.interfaces.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.health.HealthQueryService;
import me.gogradually.courseenrollmentsystem.interfaces.dto.HealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health")
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final HealthQueryService healthQueryService;

    @Operation(summary = "헬스체크", description = "서버 정상 구동 여부를 반환합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "서버 정상")
    })
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse(healthQueryService.getCurrentStatus()));
    }
}
