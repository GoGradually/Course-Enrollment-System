package me.gogradually.courseenrollmentsystem.application.enrollment.support;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EnrollmentCancellationProcessor {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;

    public void cancel(Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findByIdForUpdate(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));

        enrollment.cancel();
        enrollment.getCourse().decreaseEnrollment();

        courseRepository.save(enrollment.getCourse());
        enrollmentRepository.save(enrollment);
    }
}
