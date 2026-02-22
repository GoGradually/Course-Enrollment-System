package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentRuleValidator;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import me.gogradually.courseenrollmentsystem.support.DomainFixtureFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.DayOfWeek;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PessimisticEnrollmentStrategyRetryTest.TestConfig.class)
class PessimisticEnrollmentStrategyRetryTest {

    @jakarta.annotation.Resource(name = "pessimisticEnrollmentStrategy")
    private EnrollmentStrategy pessimisticEnrollmentStrategy;
    @jakarta.annotation.Resource
    private StudentRepository studentRepository;
    @jakarta.annotation.Resource
    private CourseRepository courseRepository;
    @jakarta.annotation.Resource
    private EnrollmentRepository enrollmentRepository;
    private Student student;
    private Course course;

    @BeforeEach
    void setUp() {
        reset(studentRepository, courseRepository, enrollmentRepository);

        var department = DomainFixtureFactory.department();
        var professor = DomainFixtureFactory.professor(department);
        this.student = DomainFixtureFactory.student(department);
        this.course = DomainFixtureFactory.course(
                "CSE101",
                3,
                30,
                0,
                DayOfWeek.TUESDAY,
                10,
                11,
                department,
                professor
        );

        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(courseRepository.save(course)).thenReturn(course);
        when(enrollmentRepository.save(any(Enrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Enrollment.class));
    }

    @Test
    void shouldRetryAndSucceedWhenLockCannotBeAcquired() {
        when(courseRepository.findByIdForUpdate(2L))
                .thenThrow(new CannotAcquireLockException("lock timeout"))
                .thenThrow(new CannotAcquireLockException("lock timeout"))
                .thenReturn(Optional.of(course));

        Enrollment enrollment = pessimisticEnrollmentStrategy.enroll(1L, 2L);

        assertEquals(student, enrollment.getStudent());
        assertEquals(course, enrollment.getCourse());
        verify(courseRepository, times(3)).findByIdForUpdate(2L);
        verify(studentRepository, times(1)).findByIdForUpdate(1L);
    }

    @Test
    void shouldRetryAndSucceedWhenLockTimeoutOccursInitially() {
        when(courseRepository.findByIdForUpdate(2L))
                .thenThrow(new LockTimeoutException("lock timeout"))
                .thenThrow(new LockTimeoutException("lock timeout"))
                .thenReturn(Optional.of(course));

        Enrollment enrollment = pessimisticEnrollmentStrategy.enroll(1L, 2L);

        assertEquals(student, enrollment.getStudent());
        assertEquals(course, enrollment.getCourse());
        verify(courseRepository, times(3)).findByIdForUpdate(2L);
        verify(studentRepository, times(1)).findByIdForUpdate(1L);
    }

    @Test
    void shouldThrowConflictWhenRetryExhaustedByPessimisticLockFailure() {
        when(courseRepository.findByIdForUpdate(2L))
                .thenThrow(new PessimisticLockingFailureException("pessimistic lock failure"));

        assertThrows(
                PessimisticLockingFailureException.class,
                () -> pessimisticEnrollmentStrategy.enroll(1L, 2L)
        );
        verify(courseRepository, times(3)).findByIdForUpdate(2L);
        verify(studentRepository, times(0)).findByIdForUpdate(1L);
    }

    @Test
    void shouldThrowConflictWhenRetryExhaustedByJpaPessimisticLockException() {
        when(courseRepository.findByIdForUpdate(2L))
                .thenThrow(new PessimisticLockException("pessimistic lock"));

        assertThrows(
                PessimisticLockException.class,
                () -> pessimisticEnrollmentStrategy.enroll(1L, 2L)
        );
        verify(courseRepository, times(3)).findByIdForUpdate(2L);
        verify(studentRepository, times(0)).findByIdForUpdate(1L);
    }

    @Configuration
    @EnableRetry
    static class TestConfig {

        @Bean
        StudentRepository studentRepository() {
            return mock(StudentRepository.class);
        }

        @Bean
        CourseRepository courseRepository() {
            return mock(CourseRepository.class);
        }

        @Bean
        EnrollmentRepository enrollmentRepository() {
            return mock(EnrollmentRepository.class);
        }

        @Bean
        EnrollmentRuleValidator enrollmentRuleValidator() {
            return mock(EnrollmentRuleValidator.class);
        }

        @Bean
        EnrollmentCancellationProcessor enrollmentCancellationProcessor() {
            return mock(EnrollmentCancellationProcessor.class);
        }

        @Bean
        EnrollmentStrategy pessimisticEnrollmentStrategy(
                StudentRepository studentRepository,
                CourseRepository courseRepository,
                EnrollmentRepository enrollmentRepository,
                EnrollmentRuleValidator enrollmentRuleValidator,
                EnrollmentCancellationProcessor enrollmentCancellationProcessor
        ) {
            return new PessimisticEnrollmentStrategy(
                    studentRepository,
                    courseRepository,
                    enrollmentRepository,
                    enrollmentRuleValidator,
                    enrollmentCancellationProcessor
            );
        }
    }
}
