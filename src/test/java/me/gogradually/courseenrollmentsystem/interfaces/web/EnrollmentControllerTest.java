package me.gogradually.courseenrollmentsystem.interfaces.web;

import me.gogradually.courseenrollmentsystem.application.enrollment.orchestration.EnrollmentCommandService;
import me.gogradually.courseenrollmentsystem.application.enrollment.orchestration.EnrollmentResult;
import me.gogradually.courseenrollmentsystem.domain.exception.DuplicateEnrollmentException;
import me.gogradually.courseenrollmentsystem.infrastructure.web.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnrollmentController.class)
@Import(GlobalExceptionHandler.class)
class EnrollmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EnrollmentCommandService enrollmentCommandService;

    @Test
    void shouldCreateEnrollment() throws Exception {
        given(enrollmentCommandService.enroll(1L, 101L)).willReturn(
                new EnrollmentResult(1001L, 1L, 101L, "ACTIVE")
        );

        mockMvc.perform(
                        post("/enrollments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").value(1001))
                .andExpect(jsonPath("$.studentId").value(1))
                .andExpect(jsonPath("$.courseId").value(101))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldCreateEnrollmentWithPessimisticStrategy() throws Exception {
        given(enrollmentCommandService.enrollWithPessimisticLock(1L, 101L)).willReturn(
                new EnrollmentResult(1001L, 1L, 101L, "ACTIVE")
        );

        mockMvc.perform(
                        post("/enrollments/pessimistic")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").value(1001))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldCreateEnrollmentWithOptimisticStrategy() throws Exception {
        given(enrollmentCommandService.enrollWithOptimisticLock(1L, 101L)).willReturn(
                new EnrollmentResult(1001L, 1L, 101L, "ACTIVE")
        );

        mockMvc.perform(
                        post("/enrollments/optimistic")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").value(1001))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldCreateEnrollmentWithAtomicStrategy() throws Exception {
        given(enrollmentCommandService.enrollWithAtomicUpdate(1L, 101L)).willReturn(
                new EnrollmentResult(1001L, 1L, 101L, "ACTIVE")
        );

        mockMvc.perform(
                        post("/enrollments/atomic")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").value(1001))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldCreateEnrollmentWithSeparatedStrategy() throws Exception {
        given(enrollmentCommandService.enrollWithSeparatedTransaction(1L, 101L)).willReturn(
                new EnrollmentResult(1001L, 1L, 101L, "ACTIVE")
        );

        mockMvc.perform(
                        post("/enrollments/separated")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").value(1001))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturnConflictWhenDuplicateEnrollmentRequested() throws Exception {
        given(enrollmentCommandService.enroll(1L, 101L))
                .willThrow(new DuplicateEnrollmentException(1L, 101L));

        mockMvc.perform(
                        post("/enrollments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnConflictWhenPessimisticLockExceptionOccurs() throws Exception {
        given(enrollmentCommandService.enrollWithPessimisticLock(1L, 101L))
                .willThrow(new CannotAcquireLockException("lock timeout"));

        mockMvc.perform(
                        post("/enrollments/pessimistic")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_CONCURRENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Enrollment request failed due to concurrency conflict"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnConflictWhenOptimisticLockExceptionOccurs() throws Exception {
        given(enrollmentCommandService.enrollWithOptimisticLock(1L, 101L))
                .willThrow(new OptimisticLockingFailureException("optimistic lock"));

        mockMvc.perform(
                        post("/enrollments/optimistic")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "studentId": 1,
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_CONCURRENCY_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Enrollment request failed due to concurrency conflict"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnBadRequestWhenRequestFieldMissing() throws Exception {
        mockMvc.perform(
                        post("/enrollments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "courseId": 101
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldCancelEnrollment() throws Exception {
        mockMvc.perform(delete("/enrollments/{enrollmentId}", 1001L))
                .andExpect(status().isNoContent());
    }
}
