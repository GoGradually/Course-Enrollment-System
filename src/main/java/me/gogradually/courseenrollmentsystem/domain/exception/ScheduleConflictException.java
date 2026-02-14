package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when requested course schedule conflicts with existing enrollments.
 */
public class ScheduleConflictException extends DomainException {

    public ScheduleConflictException(Long studentId, Long courseId) {
        super("Schedule conflict. studentId=" + studentId + ", courseId=" + courseId);
    }
}
