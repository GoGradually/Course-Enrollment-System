package me.gogradually.courseenrollmentsystem.domain.professor;

import java.util.List;

/**
 * Professor repository port.
 */
public interface ProfessorRepository {

    List<Professor> findAll(int offset, int limit);
}
