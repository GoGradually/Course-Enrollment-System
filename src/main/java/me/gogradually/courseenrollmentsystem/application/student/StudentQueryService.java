package me.gogradually.courseenrollmentsystem.application.student;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentQueryService {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final StudentRepository studentRepository;

    /**
     * Returns paged student summaries ordered by id ascending.
     */
    public List<StudentSummary> getStudents(Integer offset, Integer limit) {
        int normalizedOffset = normalizeOffset(offset);
        int normalizedLimit = normalizeLimit(limit);

        return studentRepository.findAll(normalizedOffset, normalizedLimit).stream()
            .map(StudentSummary::from)
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
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
