package me.gogradually.courseenrollmentsystem.infrastructure.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.CourseJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CourseRepositoryAdapter implements CourseRepository {

    @PersistenceContext
    private EntityManager entityManager;

    private final CourseJpaRepository courseJpaRepository;

    @Override
    public Optional<Course> findById(Long courseId) {
        return courseJpaRepository.findById(courseId);
    }

    @Override
    public Optional<Course> findByIdForUpdate(Long courseId) {
        Course course = entityManager.find(Course.class, courseId, LockModeType.PESSIMISTIC_WRITE);
        return Optional.ofNullable(course);
    }

    @Override
    public List<Course> findAll(Long departmentId, int offset, int limit) {
        StringBuilder queryBuilder = new StringBuilder(
                "select c from Course c join fetch c.department d join fetch c.professor p"
        );

        if (departmentId != null) {
            queryBuilder.append(" where d.id = :departmentId");
        }
        queryBuilder.append(" order by c.id");

        TypedQuery<Course> query = entityManager.createQuery(queryBuilder.toString(), Course.class);
        if (departmentId != null) {
            query.setParameter("departmentId", departmentId);
        }

        return query
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    @Override
    public int incrementEnrolledCountIfAvailable(Long courseId) {
        return entityManager.createQuery("""
                        update Course c
                        set c.enrolledCount = c.enrolledCount + 1
                        where c.id = :courseId
                          and c.enrolledCount < c.capacity
                        """)
                .setParameter("courseId", courseId)
                .executeUpdate();
    }

    @Override
    public int decrementEnrolledCountIfPositive(Long courseId) {
        return entityManager.createQuery("""
                        update Course c
                        set c.enrolledCount = c.enrolledCount - 1
                        where c.id = :courseId
                          and c.enrolledCount > 0
                        """)
                .setParameter("courseId", courseId)
                .executeUpdate();
    }

    @Override
    public void clearPersistenceContext() {
        entityManager.clear();
    }

    @Override
    public Course save(Course course) {
        return courseJpaRepository.save(course);
    }
}
