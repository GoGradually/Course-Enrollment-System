package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import jakarta.persistence.OptimisticLockException;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.tx.OptimisticEnrollmentTxExecutor;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentConcurrencyConflictException;
import me.gogradually.courseenrollmentsystem.support.DomainFixtureFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.DayOfWeek;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(OptimisticEnrollmentStrategyRetryTest.TestConfig.class)
class OptimisticEnrollmentStrategyRetryTest {

    @jakarta.annotation.Resource(name = "optimisticEnrollmentStrategy")
    private EnrollmentStrategy optimisticEnrollmentStrategy;
    @jakarta.annotation.Resource
    private OptimisticEnrollmentTxExecutor optimisticEnrollmentTxExecutor;

    @BeforeEach
    void setUp() {
        reset(optimisticEnrollmentTxExecutor);
    }

    @Test
    void shouldRetryAndSucceedWhenOptimisticLockFailsInitially() {
        Enrollment expected = sampleEnrollment();

        when(optimisticEnrollmentTxExecutor.executeOnce(1L, 2L))
                .thenThrow(new OptimisticLockingFailureException("first failure"))
                .thenThrow(new OptimisticLockingFailureException("second failure"))
                .thenReturn(expected);

        Enrollment result = optimisticEnrollmentStrategy.enroll(1L, 2L);

        assertSame(expected, result);
        verify(optimisticEnrollmentTxExecutor, times(3)).executeOnce(1L, 2L);
    }

    @Test
    void shouldThrowConflictWhenRetryExhausted() {
        when(optimisticEnrollmentTxExecutor.executeOnce(1L, 2L))
                .thenThrow(new OptimisticLockException("stale version"));

        EnrollmentConcurrencyConflictException exception = assertThrows(
                EnrollmentConcurrencyConflictException.class,
                () -> optimisticEnrollmentStrategy.enroll(1L, 2L)
        );

        assertTrue(exception.getMessage().contains("retryCount=3"));
        verify(optimisticEnrollmentTxExecutor, times(3)).executeOnce(1L, 2L);
    }

    private Enrollment sampleEnrollment() {
        var department = DomainFixtureFactory.department();
        var professor = DomainFixtureFactory.professor(department);
        var student = DomainFixtureFactory.student(department);
        var course = DomainFixtureFactory.course(
                "CSE101",
                3,
                30,
                0,
                DayOfWeek.MONDAY,
                9,
                10,
                department,
                professor
        );
        return Enrollment.enroll(student, course);
    }

    @Configuration
    @EnableRetry
    static class TestConfig {

        @Bean
        OptimisticEnrollmentTxExecutor optimisticEnrollmentTxExecutor() {
            return mock(OptimisticEnrollmentTxExecutor.class);
        }

        @Bean
        EnrollmentCancellationProcessor enrollmentCancellationProcessor() {
            return mock(EnrollmentCancellationProcessor.class);
        }

        @Bean
        EnrollmentStrategy optimisticEnrollmentStrategy(
                OptimisticEnrollmentTxExecutor optimisticEnrollmentTxExecutor,
                EnrollmentCancellationProcessor enrollmentCancellationProcessor
        ) {
            return new OptimisticEnrollmentStrategy(optimisticEnrollmentTxExecutor, enrollmentCancellationProcessor);
        }
    }
}
