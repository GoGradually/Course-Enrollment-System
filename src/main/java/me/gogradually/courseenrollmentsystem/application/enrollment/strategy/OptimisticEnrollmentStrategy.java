package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.tx.OptimisticEnrollmentTxExecutor;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentConcurrencyConflictException;
import org.springframework.dao.OptimisticLockingFailureException;
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
    public Enrollment enroll(Long studentId, Long courseId) {
        for (int attempt = 1; attempt <= RETRY_LIMIT; attempt++) {
            try {
                return optimisticEnrollmentTxExecutor.executeOnce(studentId, courseId);
            } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == RETRY_LIMIT) {
                    throw new EnrollmentConcurrencyConflictException(studentId, courseId, attempt);
                }
            }
        }

        throw new EnrollmentConcurrencyConflictException(studentId, courseId, RETRY_LIMIT);
    }

    @Override
    @Transactional
    public void cancel(Long enrollmentId) {
        cancellationProcessor.cancel(enrollmentId);
    }
}
