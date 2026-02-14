package me.gogradually.courseenrollmentsystem.application.professor;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.professor.ProfessorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfessorQueryService {

    private final ProfessorRepository professorRepository;

    /**
     * Returns paged professor summaries ordered by id ascending.
     */
    public List<ProfessorSummary> getProfessors(Integer offset, Integer limit) {
        int normalizedOffset = normalizeOffset(offset);
        int normalizedLimit = normalizeLimit(limit);

        return professorRepository.findAll(normalizedOffset, normalizedLimit).stream()
            .map(ProfessorSummary::from)
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
