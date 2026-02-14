package me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa;

import me.gogradually.courseenrollmentsystem.domain.course.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseJpaRepository extends JpaRepository<Course, Long> {
}
