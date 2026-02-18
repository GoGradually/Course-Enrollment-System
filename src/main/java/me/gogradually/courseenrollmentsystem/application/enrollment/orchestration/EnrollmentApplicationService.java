package me.gogradually.courseenrollmentsystem.application.enrollment.orchestration;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.strategy.EnrollmentStrategyRouter;
import me.gogradually.courseenrollmentsystem.application.enrollment.strategy.EnrollmentStrategyType;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import org.springframework.stereotype.Service;

/**
 * Application service orchestrating enrollment and cancellation use cases.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentApplicationService {

    private final EnrollmentStrategyRouter enrollmentStrategyRouter;

    /**
     * Registers a student to a course with the default strategy.
     */
    public Enrollment enroll(Long studentId, Long courseId) {
        return enrollmentStrategyRouter.get(EnrollmentStrategyType.ATOMIC).enroll(studentId, courseId);
    }

    /**
     * Registers a student with pessimistic course lock strategy.
     */
    public Enrollment enrollWithPessimisticLock(Long studentId, Long courseId) {
        return enrollmentStrategyRouter.get(EnrollmentStrategyType.PESSIMISTIC).enroll(studentId, courseId);
    }

    /**
     * Registers a student with optimistic locking strategy.
     */
    public Enrollment enrollWithOptimisticLock(Long studentId, Long courseId) {
        return enrollmentStrategyRouter.get(EnrollmentStrategyType.OPTIMISTIC).enroll(studentId, courseId);
    }

    /**
     * Registers a student with atomic update strategy.
     */
    public Enrollment enrollWithAtomicUpdate(Long studentId, Long courseId) {
        return enrollmentStrategyRouter.get(EnrollmentStrategyType.ATOMIC).enroll(studentId, courseId);
    }

    /**
     * Registers a student with separated transaction strategy.
     */
    public Enrollment enrollWithSeparatedTransaction(Long studentId, Long courseId) {
        return enrollmentStrategyRouter.get(EnrollmentStrategyType.SEPARATED).enroll(studentId, courseId);
    }

    /**
     * Cancels an active enrollment.
     */
    public void cancel(Long enrollmentId) {
        enrollmentStrategyRouter.get(EnrollmentStrategyType.ATOMIC).cancel(enrollmentId);
    }
}
