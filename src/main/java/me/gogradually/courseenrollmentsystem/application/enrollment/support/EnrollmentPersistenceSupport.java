package me.gogradually.courseenrollmentsystem.application.enrollment.support;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseCapacityExceededException;
import me.gogradually.courseenrollmentsystem.domain.exception.DuplicateEnrollmentException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentPersistenceSupport {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public Long insertActiveOrThrow(Long studentId, Long courseId) {
        try {
            return enrollmentRepository.insertActive(studentId, courseId);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateEnrollmentException(studentId, courseId);
        }
    }

    public void incrementSeatOrThrow(Long courseId, Course course) {
        int affectedRows = courseRepository.incrementEnrolledCountIfAvailable(courseId);
        if (affectedRows == 0) {
            throw new CourseCapacityExceededException(courseId, course.getCapacity());
        }
    }
}
