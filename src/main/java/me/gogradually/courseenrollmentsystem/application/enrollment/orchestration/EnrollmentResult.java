package me.gogradually.courseenrollmentsystem.application.enrollment.orchestration;

public record EnrollmentResult(
    Long enrollmentId,
    Long studentId,
    Long courseId,
    String status
) {
}
