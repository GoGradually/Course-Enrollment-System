package me.gogradually.courseenrollmentsystem.application.enrollment.support;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.DuplicateEnrollmentException;
import me.gogradually.courseenrollmentsystem.domain.exception.ScheduleConflictException;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class EnrollmentRuleValidator {

    private final EnrollmentRepository enrollmentRepository;

    public void validateForPreInsert(Long studentId, Long courseId, Student student, Course requestedCourse) {
        validateDuplicateEnrollment(studentId, courseId);
        List<Enrollment> activeEnrollments = enrollmentRepository.findActiveByStudentId(studentId);
        validateCreditAndSchedule(studentId, courseId, student, requestedCourse, activeEnrollments);
    }

    public void validateAfterAtomicInsert(
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
}
