package me.gogradually.courseenrollmentsystem.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.professor.ProfessorRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProfessorRepositoryAdapter implements ProfessorRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Professor> findAll(int offset, int limit) {
        return entityManager.createQuery(
                        "select p from Professor p join fetch p.department order by p.id",
                        Professor.class
                )
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }
}
