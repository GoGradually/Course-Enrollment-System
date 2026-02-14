package me.gogradually.courseenrollmentsystem.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record InitialDataProperties(
        boolean enabled,
        long randomSeed,
        int departmentCount,
        int professorCount,
        int studentCount,
        int courseCount,
        int batchSize
) {

    public InitialDataProperties {
        if (enabled) {
            if (departmentCount < 1) {
                throw new IllegalArgumentException("app.seed.department-count must be positive");
            }
            if (professorCount < 1) {
                throw new IllegalArgumentException("app.seed.professor-count must be positive");
            }
            if (studentCount < 1) {
                throw new IllegalArgumentException("app.seed.student-count must be positive");
            }
            if (courseCount < 1) {
                throw new IllegalArgumentException("app.seed.course-count must be positive");
            }
            if (batchSize < 1) {
                throw new IllegalArgumentException("app.seed.batch-size must be positive");
            }
        }
    }
}
