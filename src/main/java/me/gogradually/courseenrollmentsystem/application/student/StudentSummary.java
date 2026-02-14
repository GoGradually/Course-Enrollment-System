package me.gogradually.courseenrollmentsystem.application.student;

import me.gogradually.courseenrollmentsystem.domain.student.Student;

public record StudentSummary(
    Long id,
    String studentNumber,
    String name,
    Long departmentId,
    String departmentName
) {

    public static StudentSummary from(Student student) {
        return new StudentSummary(
            student.getId(),
            student.getStudentNumber(),
            student.getName(),
            student.getDepartment().getId(),
            student.getDepartment().getName()
        );
    }
}
