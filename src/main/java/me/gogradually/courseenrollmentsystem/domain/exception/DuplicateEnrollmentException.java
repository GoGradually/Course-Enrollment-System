package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when a student tries to enroll in the same course twice.
 */
public class DuplicateEnrollmentException extends DomainException {

    public DuplicateEnrollmentException(Long studentId, Long courseId) {
        super("Duplicate enrollment. studentId=" + studentId + ", courseId=" + courseId);
    }
}
