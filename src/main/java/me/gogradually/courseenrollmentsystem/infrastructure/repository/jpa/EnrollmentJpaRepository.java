package me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa;

import jakarta.persistence.LockModeType;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnrollmentJpaRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByStudent_IdAndCourse_IdAndStatus(Long studentId, Long courseId, EnrollmentStatus status);

    @Query("""
            select e
            from Enrollment e
            join fetch e.course c
            where e.student.id = :studentId
              and e.status = :status
            order by e.id
            """)
    List<Enrollment> findAllByStudentIdAndStatusWithCourse(
            @Param("studentId") Long studentId,
            @Param("status") EnrollmentStatus status
    );

    @Query("""
            select e
            from Enrollment e
            join fetch e.course c
            join fetch c.department
            join fetch c.professor
            where e.student.id = :studentId
              and e.status = :status
            order by c.id
            """)
    List<Enrollment> findAllByStudentIdAndStatusWithCourseDetails(
            @Param("studentId") Long studentId,
            @Param("status") EnrollmentStatus status
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select e
            from Enrollment e
            where e.id = :enrollmentId
            """)
    Optional<Enrollment> findByIdForUpdate(Long enrollmentId);
}
