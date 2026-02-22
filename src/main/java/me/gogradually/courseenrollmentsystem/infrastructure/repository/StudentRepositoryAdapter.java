package me.gogradually.courseenrollmentsystem.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.StudentJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class StudentRepositoryAdapter implements StudentRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final StudentJpaRepository studentJpaRepository;

    @Override
    public Optional<Student> findById(Long studentId) {
        return studentJpaRepository.findById(studentId);
    }

    @Override
    public Optional<Student> findByIdForUpdate(Long studentId) {
        Student student = entityManager.find(Student.class, studentId, LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(student);
    }

    @Override
    public boolean existsById(Long studentId) {
        return studentJpaRepository.existsById(studentId);
    }

    @Override
    public List<Student> findAll(int offset, int limit) {
        return entityManager.createQuery(
                        "select s from Student s join fetch s.department order by s.id",
                        Student.class
                )
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public Student save(Student student) {
        return studentJpaRepository.save(student);
    }
}
