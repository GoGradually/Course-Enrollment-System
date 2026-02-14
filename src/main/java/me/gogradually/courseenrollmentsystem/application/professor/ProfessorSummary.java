package me.gogradually.courseenrollmentsystem.application.professor;

import me.gogradually.courseenrollmentsystem.domain.professor.Professor;

public record ProfessorSummary(
    Long id,
    String name,
    Long departmentId,
    String departmentName
) {

    public static ProfessorSummary from(Professor professor) {
        return new ProfessorSummary(
            professor.getId(),
            professor.getName(),
            professor.getDepartment().getId(),
            professor.getDepartment().getName()
        );
    }
}
