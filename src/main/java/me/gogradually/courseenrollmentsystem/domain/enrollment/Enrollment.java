package me.gogradually.courseenrollmentsystem.domain.enrollment;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.exception.EnrollmentCancellationNotAllowedException;
import me.gogradually.courseenrollmentsystem.domain.student.Student;

import java.time.LocalDateTime;

/**
 * Enrollment entity between student and course.
 */
@Getter
@Entity
@Table(
        name = "enrollments",
        indexes = {
                @Index(name = "idx_enrollments_student_status", columnList = "student_id, status"),
                @Index(name = "idx_enrollments_course_status", columnList = "course_id, status")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_enrollments_student_course_status",
                columnNames = {"student_id", "course_id", "status"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "student_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "course_id",
            nullable = false,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime canceledAt;

    private Enrollment(Student student, Course course) {
        this.student = student;
        this.course = course;
        this.status = EnrollmentStatus.ACTIVE;
        this.createdAt = LocalDateTime.now();
    }

    public static Enrollment enroll(Student student, Course course) {
        return new Enrollment(student, course);
    }

    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }

    public void cancel() {
        if (status != EnrollmentStatus.ACTIVE) {
            throw new EnrollmentCancellationNotAllowedException(id);
        }
        status = EnrollmentStatus.CANCELED;
        canceledAt = LocalDateTime.now();
    }
}
