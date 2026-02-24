package me.gogradually.courseenrollmentsystem.application.enrollment.strategy;

import lombok.RequiredArgsConstructor;
import me.gogradually.courseenrollmentsystem.application.enrollment.support.EnrollmentCancellationProcessor;
import me.gogradually.courseenrollmentsystem.application.enrollment.tx.SeparatedEnrollmentTxExecutor;
import me.gogradually.courseenrollmentsystem.domain.enrollment.Enrollment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SeparatedEnrollmentStrategy implements EnrollmentStrategy {

    private final SeparatedEnrollmentTxExecutor separatedEnrollmentTxExecutor;
    private final EnrollmentCancellationProcessor cancellationProcessor;

    @Override
    public EnrollmentStrategyType type() {
        return EnrollmentStrategyType.SEPARATED;
    }

    @Override
    public Enrollment enroll(Long studentId, Long courseId) {
        Long enrollmentId = separatedEnrollmentTxExecutor.reserveSeat(courseId, studentId);
        try {
            return separatedEnrollmentTxExecutor.finalizeEnrollment(studentId, courseId, enrollmentId);
        } catch (RuntimeException exception) {
            try {
                separatedEnrollmentTxExecutor.releaseSeat(courseId, enrollmentId);
            } catch (RuntimeException ignored) {
                // TODO 이벤트 발행 등으로 지연 배치 처리 필요
            }
            throw exception;
        }
    }

    @Override
    @Transactional
    public void cancel(Long enrollmentId) {
        cancellationProcessor.cancel(enrollmentId);
    }
}
