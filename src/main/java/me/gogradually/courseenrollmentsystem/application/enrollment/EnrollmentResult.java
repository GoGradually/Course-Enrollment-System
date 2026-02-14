package me.gogradually.courseenrollmentsystem.application.enrollment;

public record EnrollmentResult(
    Long enrollmentId,
    Long studentId,
    Long courseId,
    String status
) {
}
