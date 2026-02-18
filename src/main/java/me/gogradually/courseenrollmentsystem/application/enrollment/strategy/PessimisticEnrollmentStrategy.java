package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentRuleValidator;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.exception.StudentNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class PessimisticEnrollmentStrategy implements EnrollmentStrategy {

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

    @Override
    @Transactional
    public void cancel(Long enrollmentId) {
        cancellationProcessor.cancel(enrollmentId);
    }
}
