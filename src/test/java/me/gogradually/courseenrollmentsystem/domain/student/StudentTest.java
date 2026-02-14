package me.gogradually.courseenrollmentsystem.domain.student;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.exception.CreditLimitExceededException;
import me.gogradually.courseenrollmentsystem.support.DomainFixtureFactory;
import org.junit.jupiter.api.Test;

class StudentTest {

    @Test
    void shouldAllowEnrollWhenTotalCreditsIsAtMostEighteen() {
        Department department = DomainFixtureFactory.department();
        Student student = DomainFixtureFactory.student(department);

        assertDoesNotThrow(() -> student.validateCreditLimit(15, 3));
    }

    @Test
    void shouldRejectEnrollWhenTotalCreditsExceedsEighteen() {
        Department department = DomainFixtureFactory.department();
        Student student = DomainFixtureFactory.student(department);

        assertThrows(CreditLimitExceededException.class, () -> student.validateCreditLimit(16, 3));
    }
}
