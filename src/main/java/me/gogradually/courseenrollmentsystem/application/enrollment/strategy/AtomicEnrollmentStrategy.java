package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentPersistenceSupport;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentRuleValidator;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.exception.StudentNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AtomicEnrollmentStrategy implements EnrollmentStrategy {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentRuleValidator ruleValidator;
    private final EnrollmentPersistenceSupport persistenceSupport;
    private final EnrollmentCancellationProcessor cancellationProcessor;

    @Override
    public EnrollmentStrategyType type() {
        return EnrollmentStrategyType.ATOMIC;
    }

    @Override
    @Transactional
    public Enrollment enroll(Long studentId, Long courseId) {
        Long enrollmentId = persistenceSupport.insertActiveOrThrow(studentId, courseId);
        persistenceSupport.incrementSeatOrThrow(courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        ruleValidator.validateAfterAtomicInsert(studentId, enrollmentId, student, course);
        courseRepository.clearPersistenceContext();

        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
    }

    @Override
    @Transactional
    public void cancel(Long enrollmentId) {
        cancellationProcessor.cancel(enrollmentId);
    }
}
