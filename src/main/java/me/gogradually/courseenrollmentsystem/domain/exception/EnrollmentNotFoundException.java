package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when an enrollment entity cannot be found.
 */
public class EnrollmentNotFoundException extends DomainException {

    public EnrollmentNotFoundException(Long enrollmentId) {
        super("Enrollment not found. enrollmentId=" + enrollmentId);
    }
}
