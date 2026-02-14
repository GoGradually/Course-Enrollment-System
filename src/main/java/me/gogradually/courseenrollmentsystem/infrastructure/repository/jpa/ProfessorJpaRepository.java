package me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa;

import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfessorJpaRepository extends JpaRepository<Professor, Long> {
}
