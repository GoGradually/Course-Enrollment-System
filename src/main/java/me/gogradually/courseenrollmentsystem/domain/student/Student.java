package me.gogradually.courseenrollmentsystem.domain.student;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.exception.CreditLimitExceededException;

/**
 * Student aggregate root.
 */
@Getter
@Entity
@Table(name = "students")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student {

    public static final int MAX_CREDITS = 18;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String studentNumber;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    public Student(String studentNumber, String name, Department department) {
        this.studentNumber = studentNumber;
        this.name = name;
        this.department = department;
    }

    /**
     * Validates requested credits against the student maximum credit policy.
     */
    public void validateCreditLimit(int currentCredits, int requestCredits) {
        if (currentCredits + requestCredits > MAX_CREDITS) {
            throw new CreditLimitExceededException(id, currentCredits, requestCredits, MAX_CREDITS);
        }
    }
}
