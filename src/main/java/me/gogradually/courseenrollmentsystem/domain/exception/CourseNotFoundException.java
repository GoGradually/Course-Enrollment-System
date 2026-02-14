package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when the requested course does not exist.
 */
public class CourseNotFoundException extends DomainException {

    public CourseNotFoundException(Long courseId) {
        super("Course not found. courseId=" + courseId);
    }
}
