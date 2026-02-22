package me.gogradually.courseenrollmentsystem.application.enrollment.support;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseCapacityExceededException;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.exception.DuplicateEnrollmentException;
import me.gogradually.courseenrollmentsystem.domain.exception.StudentNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentPersistenceSupport {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;

    public Long insertActiveOrThrow(Long studentId, Long courseId) {
        try {
            return enrollmentRepository.insertActive(studentId, courseId);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateEnrollmentException(studentId, courseId);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEnrollmentViolation(exception)) {
                throw new DuplicateEnrollmentException(studentId, courseId);
            }
            if (!studentRepository.existsById(studentId)) {
                throw new StudentNotFoundException(studentId);
            }
            if (!courseRepository.existsById(courseId)) {
                throw new CourseNotFoundException(courseId);
            }
            throw new IllegalStateException("Failed to insert active enrollment", exception);
        }
    }

    public void incrementSeatOrThrow(Long courseId, Course course) {
        int affectedRows = courseRepository.incrementEnrolledCountIfAvailable(courseId);
        if (affectedRows == 0) {
            throw new CourseCapacityExceededException(courseId, course.getCapacity());
        }
    }

    public void incrementSeatOrThrow(Long courseId) {
        int affectedRows = courseRepository.incrementEnrolledCountIfAvailable(courseId);
        if (affectedRows == 0) {
            int capacity = courseRepository.findById(courseId)
                    .map(Course::getCapacity)
                    .orElseThrow(() -> new CourseNotFoundException(courseId));
            throw new CourseCapacityExceededException(courseId, capacity);
        }
    }

    private boolean isDuplicateEnrollmentViolation(DataIntegrityViolationException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String loweredMessage = message.toLowerCase();
        return loweredMessage.contains("duplicate")
                || loweredMessage.contains("uk_enrollments_student_course_status");
    }
}
