package me.gogradually.courseenrollmentsystem.application.enrollment;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.*;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;

/**
 * Application service orchestrating enrollment and cancellation use cases.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentApplicationService {

    private static final int OPTIMISTIC_RETRY_LIMIT = 3;

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final PlatformTransactionManager transactionManager;

    /**
     * Registers a student to a course after validating all business rules.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Enrollment enroll(Long studentId, Long courseId) {
        return enrollWithAtomicUpdate(studentId, courseId);
    }

    /**
     * Registers a student with pessimistic course lock.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Enrollment enrollWithPessimisticLock(Long studentId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        Student student = studentRepository.findByIdForUpdate(studentId)
            .orElseThrow(() -> new StudentNotFoundException(studentId));

        validateEnrollmentRules(studentId, courseId, student, course);
        course.increaseEnrollment();

        Enrollment enrollment = Enrollment.enroll(student, course);
        courseRepository.save(course);
        return enrollmentRepository.save(enrollment);
    }

    /**
     * Registers a student with optimistic locking and retries.
     */
    public Enrollment enrollWithOptimisticLock(Long studentId, Long courseId) {
        TransactionTemplate transactionTemplate = createRequiresNewReadCommittedTemplate();

        for (int attempt = 1; attempt <= OPTIMISTIC_RETRY_LIMIT; attempt++) {
            try {
                Enrollment enrollment = transactionTemplate.execute(
                        status -> enrollWithOptimisticLockInNewTransaction(studentId, courseId)
                );
                return Objects.requireNonNull(enrollment, "Enrollment result must not be null");
            } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == OPTIMISTIC_RETRY_LIMIT) {
                    throw new EnrollmentConcurrencyConflictException(studentId, courseId, attempt);
                }
            }
        }

        throw new EnrollmentConcurrencyConflictException(studentId, courseId, OPTIMISTIC_RETRY_LIMIT);
    }

    /**
     * Registers a student by trying DB insert first and applying atomic seat update.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Enrollment enrollWithAtomicUpdate(Long studentId, Long courseId) {
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        Course course = courseRepository.findById(courseId)
            .orElseThrow(() -> new CourseNotFoundException(courseId));

        Long enrollmentId = insertActiveEnrollment(studentId, courseId);
        validateCreditAndScheduleAfterInsert(studentId, enrollmentId, student, course);
        incrementSeatOrThrow(courseId, course);
        courseRepository.clearPersistenceContext();

        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
    }

    private Enrollment enrollWithOptimisticLockInNewTransaction(Long studentId, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        validateEnrollmentRules(studentId, courseId, student, course);
        course.increaseEnrollment();

        Enrollment enrollment = Enrollment.enroll(student, course);
        courseRepository.save(course);
        return enrollmentRepository.save(enrollment);
    }

    private void validateEnrollmentRules(Long studentId, Long courseId, Student student, Course requestedCourse) {
        validateDuplicateEnrollment(studentId, courseId);
        List<Enrollment> activeEnrollments = enrollmentRepository.findActiveByStudentId(studentId);
        validateCreditAndSchedule(studentId, courseId, student, requestedCourse, activeEnrollments);
    }

    private void validateCreditAndScheduleAfterInsert(
            Long studentId,
            Long insertedEnrollmentId,
            Student student,
            Course requestedCourse
    ) {
        List<Enrollment> existingActiveEnrollments = enrollmentRepository.findActiveByStudentId(studentId).stream()
                .filter(enrollment -> !Objects.equals(enrollment.getId(), insertedEnrollmentId))
                .toList();

        validateCreditAndSchedule(
                studentId,
                requestedCourse.getId(),
                student,
                requestedCourse,
                existingActiveEnrollments
        );
    }

    private void validateDuplicateEnrollment(Long studentId, Long courseId) {
        if (enrollmentRepository.existsActiveByStudentIdAndCourseId(studentId, courseId)) {
            throw new DuplicateEnrollmentException(studentId, courseId);
        }
    }

    private void validateCreditAndSchedule(
            Long studentId,
            Long courseId,
            Student student,
            Course requestedCourse,
            List<Enrollment> activeEnrollments
    ) {
        int currentCredits = activeEnrollments.stream()
            .map(Enrollment::getCourse)
            .mapToInt(Course::getCredits)
            .sum();

        student.validateCreditLimit(currentCredits, requestedCourse.getCredits());

        boolean hasScheduleConflict = activeEnrollments.stream()
            .map(Enrollment::getCourse)
                .anyMatch(activeCourse -> activeCourse.hasScheduleConflictWith(requestedCourse));

        if (hasScheduleConflict) {
            throw new ScheduleConflictException(studentId, courseId);
        }
    }

    /**
     * Cancels an active enrollment.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancel(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
            .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));

        enrollment.cancel();
        enrollment.getCourse().decreaseEnrollment();

        courseRepository.save(enrollment.getCourse());
        enrollmentRepository.save(enrollment);
    }

    private Long insertActiveEnrollment(Long studentId, Long courseId) {
        try {
            return enrollmentRepository.insertActive(studentId, courseId);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateEnrollmentException(studentId, courseId);
        }
    }

    private void incrementSeatOrThrow(Long courseId, Course course) {
        int affectedRows = courseRepository.incrementEnrolledCountIfAvailable(courseId);
        if (affectedRows == 0) {
            throw new CourseCapacityExceededException(courseId, course.getCapacity());
        }
    }

    private TransactionTemplate createRequiresNewReadCommittedTemplate() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionTemplate;
    }
}
