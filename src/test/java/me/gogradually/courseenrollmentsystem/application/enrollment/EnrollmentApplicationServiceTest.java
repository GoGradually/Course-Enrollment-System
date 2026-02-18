package me.gogradually.courseenrollmentsystem.application.enrollment;

import me.gogradually.courseenrollmentsystem.application.enrollment.orchestration.EnrollmentApplicationService;
import me.gogradually.courseenrollmentsystem.application.enrollment.strategy.EnrollmentStrategy;
import me.gogradually.courseenrollmentsystem.application.enrollment.strategy.EnrollmentStrategyRouter;
import me.gogradually.courseenrollmentsystem.application.enrollment.strategy.EnrollmentStrategyType;
import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.support.DomainFixtureFactory;

import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentApplicationServiceTest {

    @Mock
    private EnrollmentStrategyRouter enrollmentStrategyRouter;

    @Mock
    private EnrollmentStrategy atomicStrategy;

    @Mock
    private EnrollmentStrategy pessimisticStrategy;

    @Mock
    private EnrollmentStrategy optimisticStrategy;

    @Mock
    private EnrollmentStrategy separatedStrategy;

    @InjectMocks
    private EnrollmentApplicationService enrollmentApplicationService;

    @Test
    void shouldRouteDefaultEnrollmentToAtomicStrategy() {
        Enrollment enrollment = sampleEnrollment();
        when(enrollmentStrategyRouter.get(EnrollmentStrategyType.ATOMIC)).thenReturn(atomicStrategy);
        when(atomicStrategy.enroll(1L, 2L)).thenReturn(enrollment);

        Enrollment result = enrollmentApplicationService.enroll(1L, 2L);

        assertSame(enrollment, result);
        verify(atomicStrategy).enroll(1L, 2L);
    }

    @Test
    void shouldRoutePessimisticEnrollmentToPessimisticStrategy() {
        Enrollment enrollment = sampleEnrollment();
        when(enrollmentStrategyRouter.get(EnrollmentStrategyType.PESSIMISTIC)).thenReturn(pessimisticStrategy);
        when(pessimisticStrategy.enroll(1L, 2L)).thenReturn(enrollment);

        Enrollment result = enrollmentApplicationService.enrollWithPessimisticLock(1L, 2L);

        assertSame(enrollment, result);
        verify(pessimisticStrategy).enroll(1L, 2L);
    }

    @Test
    void shouldRouteOptimisticEnrollmentToOptimisticStrategy() {
        Enrollment enrollment = sampleEnrollment();
        when(enrollmentStrategyRouter.get(EnrollmentStrategyType.OPTIMISTIC)).thenReturn(optimisticStrategy);
        when(optimisticStrategy.enroll(1L, 2L)).thenReturn(enrollment);

        Enrollment result = enrollmentApplicationService.enrollWithOptimisticLock(1L, 2L);

        assertSame(enrollment, result);
        verify(optimisticStrategy).enroll(1L, 2L);
    }

    @Test
    void shouldRouteAtomicEnrollmentToAtomicStrategy() {
        Enrollment enrollment = sampleEnrollment();
        when(enrollmentStrategyRouter.get(EnrollmentStrategyType.ATOMIC)).thenReturn(atomicStrategy);
        when(atomicStrategy.enroll(1L, 2L)).thenReturn(enrollment);

        Enrollment result = enrollmentApplicationService.enrollWithAtomicUpdate(1L, 2L);

        assertSame(enrollment, result);
        verify(atomicStrategy).enroll(1L, 2L);
    }

    @Test
    void shouldRouteSeparatedEnrollmentToSeparatedStrategy() {
        Enrollment enrollment = sampleEnrollment();
        when(enrollmentStrategyRouter.get(EnrollmentStrategyType.SEPARATED)).thenReturn(separatedStrategy);
        when(separatedStrategy.enroll(1L, 2L)).thenReturn(enrollment);

        Enrollment result = enrollmentApplicationService.enrollWithSeparatedTransaction(1L, 2L);

        assertSame(enrollment, result);
        verify(separatedStrategy).enroll(1L, 2L);
    }

    @Test
    void shouldRouteCancelToAtomicStrategy() {
        when(enrollmentStrategyRouter.get(EnrollmentStrategyType.ATOMIC)).thenReturn(atomicStrategy);

        enrollmentApplicationService.cancel(10L);

        verify(atomicStrategy).cancel(10L);
    }

    private Enrollment sampleEnrollment() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course(
                "CSE101",
                3,
                30,
                0,
                DayOfWeek.MONDAY,
                9,
                10,
                department,
                professor
        );
        return Enrollment.enroll(student, course);
    }
}
