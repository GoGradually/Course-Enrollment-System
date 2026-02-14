package me.gogradually.courseenrollmentsystem.interfaces.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.EnrollmentCommandService;
import me.gogradually.courseenrollmentsystem.interfaces.dto.EnrollmentRequest;
import me.gogradually.courseenrollmentsystem.interfaces.dto.EnrollmentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Enrollments")
@RestController
@RequestMapping("/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentCommandService enrollmentCommandService;

    @Operation(summary = "수강신청")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "수강신청 성공"),
            @ApiResponse(responseCode = "404", description = "학생 또는 강좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "중복 신청"),
            @ApiResponse(responseCode = "422", description = "학점/시간표/정원 규칙 위반")
    })
    @PostMapping
    public ResponseEntity<EnrollmentResponse> enroll(@Valid @RequestBody EnrollmentRequest request) {
        EnrollmentResponse response = EnrollmentResponse.from(
                enrollmentCommandService.enroll(request.studentId(), request.courseId())
        );
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "수강신청 - 비관적 락 전략")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "수강신청 성공"),
            @ApiResponse(responseCode = "404", description = "학생 또는 강좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "중복 신청 또는 동시성 충돌"),
            @ApiResponse(responseCode = "422", description = "학점/시간표/정원 규칙 위반")
    })
    @PostMapping("/pessimistic")
    public ResponseEntity<EnrollmentResponse> enrollWithPessimisticLock(
            @Valid @RequestBody EnrollmentRequest request
    ) {
        EnrollmentResponse response = EnrollmentResponse.from(
                enrollmentCommandService.enrollWithPessimisticLock(request.studentId(), request.courseId())
        );
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "수강신청 - 낙관적 락 전략")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "수강신청 성공"),
            @ApiResponse(responseCode = "404", description = "학생 또는 강좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "중복 신청 또는 동시성 충돌"),
            @ApiResponse(responseCode = "422", description = "학점/시간표/정원 규칙 위반")
    })
    @PostMapping("/optimistic")
    public ResponseEntity<EnrollmentResponse> enrollWithOptimisticLock(
            @Valid @RequestBody EnrollmentRequest request
    ) {
        EnrollmentResponse response = EnrollmentResponse.from(
                enrollmentCommandService.enrollWithOptimisticLock(request.studentId(), request.courseId())
        );
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "수강신청 - 원자적 업데이트 전략")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "수강신청 성공"),
            @ApiResponse(responseCode = "404", description = "학생 또는 강좌를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "중복 신청 또는 동시성 충돌"),
            @ApiResponse(responseCode = "422", description = "학점/시간표/정원 규칙 위반")
    })
    @PostMapping("/atomic")
    public ResponseEntity<EnrollmentResponse> enrollWithAtomicUpdate(
            @Valid @RequestBody EnrollmentRequest request
    ) {
        EnrollmentResponse response = EnrollmentResponse.from(
                enrollmentCommandService.enrollWithAtomicUpdate(request.studentId(), request.courseId())
        );
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "수강취소")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "수강취소 성공"),
            @ApiResponse(responseCode = "404", description = "신청 정보를 찾을 수 없음"),
            @ApiResponse(responseCode = "409", description = "이미 취소된 신청")
    })
    @DeleteMapping("/{enrollmentId}")
    public ResponseEntity<Void> cancel(@PathVariable Long enrollmentId) {
        enrollmentCommandService.cancel(enrollmentId);
        return ResponseEntity.noContent().build();
    }
}
