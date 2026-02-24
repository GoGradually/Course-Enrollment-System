package me.gogradually.courseenrollmentsystem.domain.enrollment;

import java.util.List;
import java.util.Optional;

/**
 * Enrollment repository port.
 */
public interface EnrollmentRepository {

    Optional<Enrollment> findById(Long enrollmentId);

    Long insertActive(Long studentId, Long courseId);

    Enrollment save(Enrollment enrollment);

    boolean existsActiveByStudentIdAndCourseId(Long studentId, Long courseId);

    List<Enrollment> findActiveByStudentId(Long studentId);

    List<Enrollment> findActiveByStudentIdWithCourse(Long studentId);

    Optional<Enrollment> findByIdForUpdate(Long enrollmentId);

    void deleteById(Long enrollmentId);
}
