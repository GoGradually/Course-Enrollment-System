package me.gogradually.courseenrollmentsystem.domain.student;

import java.util.List;
import java.util.Optional;

/**
 * Student repository port.
 */
public interface StudentRepository {

    Optional<Student> findById(Long studentId);

    Optional<Student> findByIdForUpdate(Long studentId);

    boolean existsById(Long studentId);

    List<Student> findAll(int offset, int limit);

    Student save(Student student);
}
