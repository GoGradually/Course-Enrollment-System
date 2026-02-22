package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.tx.OptimisticEnrollmentTxExecutor;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentConcurrencyConflictException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OptimisticEnrollmentStrategy implements EnrollmentStrategy {

    private static final int RETRY_LIMIT = 3;

    private final OptimisticEnrollmentTxExecutor optimisticEnrollmentTxExecutor;
    private final EnrollmentCancellationProcessor cancellationProcessor;

    @Override
    public EnrollmentStrategyType type() {
        return EnrollmentStrategyType.OPTIMISTIC;
    }

    @Override
    @Retryable(
            retryFor = {OptimisticLockingFailureException.class, OptimisticLockException.class},
            maxAttempts = RETRY_LIMIT,
            backoff = @Backoff(delay = 0)
    )
    public Enrollment enroll(Long studentId, Long courseId) {
        return optimisticEnrollmentTxExecutor.executeOnce(studentId, courseId);
    }

    @Recover
    public Enrollment recover(OptimisticLockingFailureException exception, Long studentId, Long courseId) {
        throw new EnrollmentConcurrencyConflictException(studentId, courseId, RETRY_LIMIT);
    }

    @Recover
    public Enrollment recover(OptimisticLockException exception, Long studentId, Long courseId) {
        throw new EnrollmentConcurrencyConflictException(studentId, courseId, RETRY_LIMIT);
    }

    @Override
    @Transactional
    public void cancel(Long enrollmentId) {
        cancellationProcessor.cancel(enrollmentId);
    }
}
