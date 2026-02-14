package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when course enrollment capacity is exceeded.
 */
public class CourseCapacityExceededException extends DomainException {

    public CourseCapacityExceededException(Long courseId, int capacity) {
        super("Course capacity exceeded. courseId=" + courseId + ", capacity=" + capacity);
    }
}
