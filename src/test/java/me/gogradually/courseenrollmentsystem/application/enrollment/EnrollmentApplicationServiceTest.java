package me.gogradually.courseenrollmentsystem.application.enrollment;

import me.gogradually.courseenrollmentsystem.domain.course.Course;
import me.gogradually.courseenrollmentsystem.domain.course.CourseRepository;
import me.gogradually.courseenrollmentsystem.domain.department.Department;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import me.gogradually.courseenrollmentsystem.domain.enrollment.EnrollmentRepository;
import me.gogradually.courseenrollmentsystem.domain.exception.*;
import me.gogradually.courseenrollmentsystem.domain.professor.Professor;
import me.gogradually.courseenrollmentsystem.domain.student.Student;
import me.gogradually.courseenrollmentsystem.domain.student.StudentRepository;
import me.gogradually.courseenrollmentsystem.support.DomainFixtureFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentApplicationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private EnrollmentApplicationService enrollmentApplicationService;

    @Test
    void shouldEnrollWhenAllBusinessRulesSatisfied() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course("선형대수", 3, 30, 10, DayOfWeek.MONDAY, 9, 10, department, professor);
        Enrollment persisted = Enrollment.enroll(student, course);

        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.insertActive(1L, 2L)).thenReturn(10L);
        when(enrollmentRepository.findActiveByStudentId(1L)).thenReturn(List.of());
        when(courseRepository.incrementEnrolledCountIfAvailable(2L)).thenReturn(1);
        when(enrollmentRepository.findById(10L)).thenReturn(Optional.of(persisted));

        Enrollment enrollment = enrollmentApplicationService.enroll(1L, 2L);

        assertTrue(enrollment.isActive());
        verify(enrollmentRepository).insertActive(1L, 2L);
        verify(courseRepository).incrementEnrolledCountIfAvailable(2L);
    }

    @Test
    void shouldRejectDuplicateEnrollment() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course("확률통계", 3, 30, 10, DayOfWeek.MONDAY, 11, 12, department, professor);

        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.insertActive(1L, 2L)).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(DuplicateEnrollmentException.class, () -> enrollmentApplicationService.enroll(1L, 2L));
    }

    @Test
    void shouldRejectWhenCreditLimitExceeded() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course requested = DomainFixtureFactory.course("인공지능", 3, 30, 10, DayOfWeek.MONDAY, 13, 14, department, professor);
        Course existing = DomainFixtureFactory.course("전공세미나", 18, 30, 10, DayOfWeek.TUESDAY, 9, 10, department, professor);
        Enrollment existingEnrollment = Enrollment.enroll(student, existing);

        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(requested));
        when(enrollmentRepository.insertActive(1L, 2L)).thenReturn(10L);
        when(enrollmentRepository.findActiveByStudentId(1L)).thenReturn(List.of(existingEnrollment));

        assertThrows(CreditLimitExceededException.class, () -> enrollmentApplicationService.enroll(1L, 2L));
    }

    @Test
    void shouldRejectWhenScheduleConflicts() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course requested = DomainFixtureFactory.course("컴파일러", 3, 30, 5, DayOfWeek.WEDNESDAY, 10, 12, department, professor);
        Course existing = DomainFixtureFactory.course("데이터통신", 3, 30, 5, DayOfWeek.WEDNESDAY, 11, 13, department, professor);
        Enrollment existingEnrollment = Enrollment.enroll(student, existing);

        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(requested));
        when(enrollmentRepository.insertActive(1L, 2L)).thenReturn(10L);
        when(enrollmentRepository.findActiveByStudentId(1L)).thenReturn(List.of(existingEnrollment));

        assertThrows(ScheduleConflictException.class, () -> enrollmentApplicationService.enroll(1L, 2L));
    }

    @Test
    void shouldRejectWhenCourseCapacityExceeded() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course("정보보호", 3, 1, 1, DayOfWeek.FRIDAY, 9, 10, department, professor);

        when(studentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(courseRepository.findById(2L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.insertActive(1L, 2L)).thenReturn(10L);
        when(enrollmentRepository.findActiveByStudentId(1L)).thenReturn(List.of());
        when(courseRepository.incrementEnrolledCountIfAvailable(2L)).thenReturn(0);

        assertThrows(CourseCapacityExceededException.class, () -> enrollmentApplicationService.enroll(1L, 2L));
    }

    @Test
    void shouldCancelEnrollmentAndDecreaseCourseCount() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course("운영체제", 3, 30, 5, DayOfWeek.THURSDAY, 13, 14, department, professor);
        Enrollment enrollment = Enrollment.enroll(student, course);

        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));
        when(courseRepository.save(any(Course.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        enrollmentApplicationService.cancel(10L);

        assertEquals(4, course.getEnrolledCount());
        assertEquals("CANCELED", enrollment.getStatus().name());
    }

    @Test
    void shouldRejectWhenCancelingAlreadyCanceledEnrollment() {
        Department department = DomainFixtureFactory.department();
        Professor professor = DomainFixtureFactory.professor(department);
        Student student = DomainFixtureFactory.student(department);
        Course course = DomainFixtureFactory.course("분산시스템", 3, 30, 5, DayOfWeek.THURSDAY, 15, 16, department, professor);
        Enrollment enrollment = Enrollment.enroll(student, course);
        enrollment.cancel();

        when(enrollmentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(enrollment));

        assertThrows(
            EnrollmentCancellationNotAllowedException.class,
            () -> enrollmentApplicationService.cancel(10L)
        );
    }
}
