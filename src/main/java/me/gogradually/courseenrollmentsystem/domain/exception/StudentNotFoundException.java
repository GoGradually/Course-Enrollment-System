package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when the requested student does not exist.
 */
public class StudentNotFoundException extends DomainException {

    public StudentNotFoundException(Long studentId) {
        super("Student not found. studentId=" + studentId);
    }
}
