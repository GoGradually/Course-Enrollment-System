package me.gogradually.courseenrollmentsystem.application.timetable;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.StudentNotFoundException;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimetableQueryService {

    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;

    /**
     * Returns active course timetable for a student in the current semester.
     */
    public StudentTimetable getStudentTimetable(Long studentId) {
        studentRepository.findById(studentId).orElseThrow(() -> new StudentNotFoundException(studentId));

        List<TimetableCourseSummary> courses = enrollmentRepository.findActiveByStudentIdWithCourse(studentId).stream()
            .map(Enrollment::getCourse)
            .map(TimetableCourseSummary::from)
            .toList();

        int totalCredits = courses.stream()
            .mapToInt(TimetableCourseSummary::credits)
            .sum();

        return new StudentTimetable(studentId, totalCredits, courses);
    }
}
