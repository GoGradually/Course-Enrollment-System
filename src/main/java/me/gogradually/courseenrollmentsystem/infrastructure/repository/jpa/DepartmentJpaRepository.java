package me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa;

import me.gogradually.courseenrollmentsystem.domain.department.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentJpaRepository extends JpaRepository<Department, Long> {
}
