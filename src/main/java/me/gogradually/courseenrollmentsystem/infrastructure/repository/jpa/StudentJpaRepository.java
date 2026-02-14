package me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa;

import me.gogradually.courseenrollmentsystem.domain.student.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentJpaRepository extends JpaRepository<Student, Long> {
}
