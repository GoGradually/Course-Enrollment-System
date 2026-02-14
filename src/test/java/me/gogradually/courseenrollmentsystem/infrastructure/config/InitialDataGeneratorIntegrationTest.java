package me.gogradually.courseenrollmentsystem.infrastructure.config;

import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.CourseJpaRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.DepartmentJpaRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.ProfessorJpaRepository;
import me.gogradually.courseenrollmentsystem.infrastructure.repository.jpa.StudentJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.seed.enabled=true",
        "app.seed.random-seed=20260208",
        "app.seed.department-count=10",
        "app.seed.professor-count=100",
        "app.seed.student-count=10000",
        "app.seed.course-count=500",
        "app.seed.batch-size=500"
})
class InitialDataGeneratorIntegrationTest {

    @Autowired
    private InitialDataGenerator initialDataGenerator;

    @Autowired
    private DepartmentJpaRepository departmentJpaRepository;

    @Autowired
    private ProfessorJpaRepository professorJpaRepository;

    @Autowired
    private StudentJpaRepository studentJpaRepository;

    @Autowired
    private CourseJpaRepository courseJpaRepository;

    @Test
    void shouldGenerateRequiredInitialDataAndSkipWhenAlreadySeeded() {
        assertThat(departmentJpaRepository.count()).isGreaterThanOrEqualTo(10);
        assertThat(professorJpaRepository.count()).isGreaterThanOrEqualTo(100);
        assertThat(studentJpaRepository.count()).isGreaterThanOrEqualTo(10_000);
        assertThat(courseJpaRepository.count()).isGreaterThanOrEqualTo(500);

        assertThat(studentJpaRepository.findAll(PageRequest.of(0, 50)).stream().map(Student::getName))
                .allMatch(name -> !name.matches("User\\d+"));
        assertThat(courseJpaRepository.findAll(PageRequest.of(0, 50)).stream().map(Course::getName))
                .allMatch(name -> !name.matches("Course\\d+"));

        long beforeDepartmentCount = departmentJpaRepository.count();
        long beforeProfessorCount = professorJpaRepository.count();
        long beforeStudentCount = studentJpaRepository.count();
        long beforeCourseCount = courseJpaRepository.count();

        initialDataGenerator.generateIfEmpty();

        assertThat(departmentJpaRepository.count()).isEqualTo(beforeDepartmentCount);
        assertThat(professorJpaRepository.count()).isEqualTo(beforeProfessorCount);
        assertThat(studentJpaRepository.count()).isEqualTo(beforeStudentCount);
        assertThat(courseJpaRepository.count()).isEqualTo(beforeCourseCount);
    }
}
