package me.gogradually.courseenrollmentsystem.infrastructure.repository;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentStatus;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.EnrollmentJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EnrollmentRepositoryAdapter implements EnrollmentRepository {

    private final EnrollmentJpaRepository enrollmentJpaRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<Enrollment> findById(Long enrollmentId) {
        return enrollmentJpaRepository.findById(enrollmentId);
    }

    @Override
    public Long insertActive(Long studentId, Long courseId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int affectedRows = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                            insert into enrollments (student_id, course_id, status, created_at)
                            values (?, ?, ?, ?)
                            """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, studentId);
            statement.setLong(2, courseId);
            statement.setString(3, EnrollmentStatus.ACTIVE.name());
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            return statement;
        }, keyHolder);

        if (affectedRows != 1 || keyHolder.getKey() == null) {
            throw new IllegalStateException("Failed to insert active enrollment");
        }

        return keyHolder.getKey().longValue();
    }

    @Override
    public Enrollment save(Enrollment enrollment) {
        return enrollmentJpaRepository.save(enrollment);
    }

    @Override
    public boolean existsActiveByStudentIdAndCourseId(Long studentId, Long courseId) {
        return enrollmentJpaRepository.existsByStudent_IdAndCourse_IdAndStatus(
            studentId,
            courseId,
            EnrollmentStatus.ACTIVE
        );
    }

    @Override
    public List<Enrollment> findActiveByStudentId(Long studentId) {
        return enrollmentJpaRepository.findAllByStudentIdAndStatusWithCourse(studentId, EnrollmentStatus.ACTIVE);
    }

    @Override
    public List<Enrollment> findActiveByStudentIdWithCourse(Long studentId) {
        return enrollmentJpaRepository.findAllByStudentIdAndStatusWithCourseDetails(
                studentId,
                EnrollmentStatus.ACTIVE
        );
    }

    @Override
    public Optional<Enrollment> findByIdForUpdate(Long enrollmentId) {
        return enrollmentJpaRepository.findByIdForUpdate(enrollmentId);
    }
}
