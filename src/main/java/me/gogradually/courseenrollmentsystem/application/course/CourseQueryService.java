package me.gogradually.courseenrollmentsystem.application.course;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private final CourseRepository courseRepository;

    /**
     * Returns courses optionally filtered by department id.
     */
    public List<CourseSummary> getCourses(Long departmentId, Integer offset, Integer limit) {
        int normalizedOffset = normalizeOffset(offset);
        int normalizedLimit = normalizeLimit(limit);

        return courseRepository.findAll(departmentId, normalizedOffset, normalizedLimit).stream()
            .map(CourseSummary::from)
            .toList();
    }

    private int normalizeOffset(Integer offset) {
        if (offset == null || offset < 0) {
            return 0;
        }
        return offset;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}
