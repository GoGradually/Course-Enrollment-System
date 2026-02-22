package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentRuleValidator;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentConcurrencyConflictException;
import me.gogradually.courseenrollmentsystem.domain.exception.StudentNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PessimisticEnrollmentStrategy implements EnrollmentStrategy {

    private static final int RETRY_LIMIT = 3;

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentRuleValidator ruleValidator;
    private final EnrollmentCancellationProcessor cancellationProcessor;

    @Override
    public EnrollmentStrategyType type() {
        return EnrollmentStrategyType.PESSIMISTIC;
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = {
                    CannotAcquireLockException.class,
                    PessimisticLockingFailureException.class
            },
            maxAttempts = RETRY_LIMIT,
            backoff = @Backoff(delay = 0)
    )
    public Enrollment enroll(Long studentId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        ruleValidator.validateForPreInsert(studentId, courseId, student, course);
        course.increaseEnrollment();

        Enrollment enrollment = Enrollment.enroll(student, course);
        courseRepository.save(course);
        return enrollmentRepository.save(enrollment);
    }

    @Recover
    public Enrollment recover(CannotAcquireLockException exception, Long studentId, Long courseId) {
        throw new EnrollmentConcurrencyConflictException(studentId, courseId, RETRY_LIMIT);
    }

    @Recover
    public Enrollment recover(PessimisticLockingFailureException exception, Long studentId, Long courseId) {
        throw new EnrollmentConcurrencyConflictException(studentId, courseId, RETRY_LIMIT);
    }

    @Override
    @Transactional
    public void cancel(Long enrollmentId) {
        cancellationProcessor.cancel(enrollmentId);
    }
}
