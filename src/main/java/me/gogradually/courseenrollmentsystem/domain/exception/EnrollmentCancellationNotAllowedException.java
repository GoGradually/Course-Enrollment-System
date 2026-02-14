package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when cancellation is requested for an already canceled enrollment.
 */
public class EnrollmentCancellationNotAllowedException extends DomainException {

    public EnrollmentCancellationNotAllowedException(Long enrollmentId) {
        super("Enrollment cancellation not allowed. enrollmentId=" + enrollmentId);
    }
}
