package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when enrollment cannot complete due to persistent concurrency conflicts.
 */
public class EnrollmentConcurrencyConflictException extends DomainException {

    public EnrollmentConcurrencyConflictException(Long studentId, Long courseId, int retryCount) {
        super(
                "Enrollment concurrency conflict. studentId=" + studentId
                        + ", courseId=" + courseId
                        + ", retryCount=" + retryCount
        );
    }
}
