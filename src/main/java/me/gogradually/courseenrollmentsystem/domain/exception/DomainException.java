package me.gogradually.courseenrollmentsystem.domain.exception;

/**
 * Base class for domain-level business exceptions.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
