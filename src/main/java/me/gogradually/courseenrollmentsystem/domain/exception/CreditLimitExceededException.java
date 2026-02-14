package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Thrown when a student exceeds the credit limit.
 */
public class CreditLimitExceededException extends DomainException {

    public CreditLimitExceededException(Long studentId, int currentCredits, int requestCredits, int maxCredits) {
        super("Credit limit exceeded. studentId=" + studentId
            + ", currentCredits=" + currentCredits
            + ", requestCredits=" + requestCredits
            + ", maxCredits=" + maxCredits);
    }
}
