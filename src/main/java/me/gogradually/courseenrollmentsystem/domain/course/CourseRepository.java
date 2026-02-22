package me.gogradually.courseenrollmentsystem.domain.course;

import java.util.List;
import java.util.Optional;

/**
 * Course repository port.
 */
public interface CourseRepository {

    Optional<Course> findById(Long courseId);

    Optional<Course> findByIdForUpdate(Long courseId);

    boolean existsById(Long courseId);

    List<Course> findAll(Long departmentId, int offset, int limit);

    int incrementEnrolledCountIfAvailable(Long courseId);

    int decrementEnrolledCountIfPositive(Long courseId);

    void clearPersistenceContext();

    Course save(Course course);
}
