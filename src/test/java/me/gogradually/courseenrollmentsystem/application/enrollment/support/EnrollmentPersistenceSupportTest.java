package me.gogradually.courseenrollmentsystem.application.enrollment.support;

import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseCapacityExceededException;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.exception.DuplicateEnrollmentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentPersistenceSupportTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private EnrollmentPersistenceSupport enrollmentPersistenceSupport;

    @Test
    void shouldThrowDuplicateEnrollmentWhenDuplicateKeyViolationOccurs() {
        when(enrollmentRepository.insertActive(1L, 2L))
                .thenThrow(new DuplicateKeyException("duplicate key"));

        assertThrows(
                DuplicateEnrollmentException.class,
                () -> enrollmentPersistenceSupport.insertActiveOrThrow(1L, 2L)
        );
    }

    @Test
    void shouldThrowDuplicateEnrollmentWhenIntegrityViolationRepresentsDuplicate() {
        when(enrollmentRepository.insertActive(1L, 2L))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key uk_enrollments_student_course_status"));

        assertThrows(
                DuplicateEnrollmentException.class,
                () -> enrollmentPersistenceSupport.insertActiveOrThrow(1L, 2L)
        );
    }

    @Test
    void shouldThrowIllegalStateWhenIntegrityViolationOccursAndIsNotDuplicate() {
        DataIntegrityViolationException integrityViolation = new DataIntegrityViolationException("integrity violation");
        when(enrollmentRepository.insertActive(1L, 2L)).thenThrow(integrityViolation);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> enrollmentPersistenceSupport.insertActiveOrThrow(1L, 2L)
        );
        assertSame(integrityViolation, exception.getCause());
    }

    @Test
    void shouldThrowCourseCapacityExceededWhenSeatIncrementCannotProceed() {
        Course course = mock(Course.class);
        when(courseRepository.incrementEnrolledCountIfAvailable(2L)).thenReturn(0);
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));
        when(course.getCapacity()).thenReturn(30);

        assertThrows(
                CourseCapacityExceededException.class,
                () -> enrollmentPersistenceSupport.incrementSeatOrThrow(2L)
        );
    }

    @Test
    void shouldThrowCourseNotFoundWhenSeatIncrementCannotProceedAndCourseMissing() {
        when(courseRepository.incrementEnrolledCountIfAvailable(2L)).thenReturn(0);
        when(courseRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(
                CourseNotFoundException.class,
                () -> enrollmentPersistenceSupport.incrementSeatOrThrow(2L)
        );
    }
}
