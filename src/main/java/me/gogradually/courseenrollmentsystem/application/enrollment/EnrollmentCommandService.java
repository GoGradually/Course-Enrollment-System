package me.gogradually.courseenrollmentsystem.application.enrollment;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnrollmentCommandService {

    private final EnrollmentApplicationService enrollmentApplicationService;

    /**
     * Enrolls a student to course and returns response-friendly fields.
     */
    public EnrollmentResult enroll(Long studentId, Long courseId) {
        Enrollment enrollment = enrollmentApplicationService.enroll(studentId, courseId);
        return toResult(enrollment);
    }

    public EnrollmentResult enrollWithPessimisticLock(Long studentId, Long courseId) {
        Enrollment enrollment = enrollmentApplicationService.enrollWithPessimisticLock(studentId, courseId);
        return toResult(enrollment);
    }

    public EnrollmentResult enrollWithOptimisticLock(Long studentId, Long courseId) {
        Enrollment enrollment = enrollmentApplicationService.enrollWithOptimisticLock(studentId, courseId);
        return toResult(enrollment);
    }

    public EnrollmentResult enrollWithAtomicUpdate(Long studentId, Long courseId) {
        Enrollment enrollment = enrollmentApplicationService.enrollWithAtomicUpdate(studentId, courseId);
        return toResult(enrollment);
    }

    private EnrollmentResult toResult(Enrollment enrollment) {
        return new EnrollmentResult(
            enrollment.getId(),
            enrollment.getStudent().getId(),
            enrollment.getCourse().getId(),
            enrollment.getStatus().name()
        );
    }

    /**
     * Cancels enrollment by enrollment id.
     */
    public void cancel(Long enrollmentId) {
        enrollmentApplicationService.cancel(enrollmentId);
    }
}
