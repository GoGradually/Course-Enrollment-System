package me.gogradually.courseenrollmentsystem.domain.course;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.exception.CourseCapacityExceededException;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;

/**
 * Course aggregate root.
 */
@Getter
@Entity
@Table(name = "courses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String courseCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int credits;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int enrolledCount;

    @Version
    private Long version;

    @Embedded
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    public Course(
        String courseCode,
        String name,
        int credits,
        int capacity,
        int enrolledCount,
        TimeSlot timeSlot,
        Department department,
        Professor professor
    ) {
        if (credits <= 0) {
            throw new IllegalArgumentException("credits must be positive");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (enrolledCount < 0 || enrolledCount > capacity) {
            throw new IllegalArgumentException("enrolledCount must be between 0 and capacity");
        }

        this.courseCode = courseCode;
        this.name = name;
        this.credits = credits;
        this.capacity = capacity;
        this.enrolledCount = enrolledCount;
        this.timeSlot = timeSlot;
        this.department = department;
        this.professor = professor;
    }

    public boolean hasScheduleConflictWith(Course other) {
        if (other == null) {
            throw new IllegalArgumentException("other course must not be null");
        }
        TimeSlot otherTimeSlot = other.getTimeSlot();
        if (timeSlot == null || otherTimeSlot == null) {
            throw new IllegalArgumentException("course timeslot must not be null");
        }
        return timeSlot.overlaps(otherTimeSlot);
    }

    public void increaseEnrollment() {
        if (enrolledCount >= capacity) {
            throw new CourseCapacityExceededException(id, capacity);
        }
        enrolledCount++;
    }

    public void decreaseEnrollment() {
        if (enrolledCount > 0) {
            enrolledCount--;
        }
    }
}
