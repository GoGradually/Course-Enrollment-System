package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;

public interface EnrollmentStrategy {

    EnrollmentStrategyType type();

    Enrollment enroll(Long studentId, Long courseId);

    void cancel(Long enrollmentId);
}
