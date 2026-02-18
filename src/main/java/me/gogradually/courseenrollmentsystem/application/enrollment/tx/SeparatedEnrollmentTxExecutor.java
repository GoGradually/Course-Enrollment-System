package me.gogradually.courseenrollmentsystem.application.enrollment.tx;

import lombok.RequiredArgsConstructor;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SeparatedEnrollmentTxExecutor {

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentRuleValidator ruleValidator;
    private final EnrollmentPersistenceSupport persistenceSupport;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveSeat(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        persistenceSupport.incrementSeatOrThrow(courseId, course);
        courseRepository.clearPersistenceContext();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Enrollment finalizeEnrollment(Long studentId, Long courseId) {
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new StudentNotFoundException(studentId));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        ruleValidator.validateForPreInsert(studentId, courseId, student, course);
        Long enrollmentId = persistenceSupport.insertActiveOrThrow(studentId, courseId);
        courseRepository.clearPersistenceContext();

        return enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseSeat(Long courseId) {
        courseRepository.decrementEnrolledCountIfPositive(courseId);
        courseRepository.clearPersistenceContext();
    }
}
