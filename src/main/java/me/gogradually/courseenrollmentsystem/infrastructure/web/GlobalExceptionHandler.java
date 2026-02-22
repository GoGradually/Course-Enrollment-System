package me.gogradually.courseenrollmentsystem.infrastructure.web;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolationException;
import me.gogradually.courseenrollmentsystem.domain.exception.*;
import me.gogradually.courseenrollmentsystem.interfaces.dto.ErrorResponse;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
        return buildResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", extractMessage(exception));
    }

    @ExceptionHandler({
            StudentNotFoundException.class,
            CourseNotFoundException.class,
            EnrollmentNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(DomainException exception) {
        return buildResponse(HttpStatus.NOT_FOUND, toCode(exception), exception.getMessage());
    }

    @ExceptionHandler({
            DuplicateEnrollmentException.class,
            EnrollmentCancellationNotAllowedException.class,
            EnrollmentConcurrencyConflictException.class
    })
    public ResponseEntity<ErrorResponse> handleConflict(DomainException exception) {
        return buildResponse(HttpStatus.CONFLICT, toCode(exception), exception.getMessage());
    }

    @ExceptionHandler({
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            OptimisticLockingFailureException.class,
            CannotSerializeTransactionException.class,
            LockTimeoutException.class,
            OptimisticLockException.class,
            PessimisticLockException.class,
            ExhaustedRetryException.class
    })
    public ResponseEntity<ErrorResponse> handleConcurrencyConflict(Exception exception) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "ENROLLMENT_CONCURRENCY_CONFLICT",
                "Enrollment request failed due to concurrency conflict"
        );
    }

    @ExceptionHandler({
            CreditLimitExceededException.class,
            ScheduleConflictException.class,
            CourseCapacityExceededException.class
    })
    public ResponseEntity<ErrorResponse> handleUnprocessable(DomainException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, toCode(exception), exception.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException exception) {
        return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, toCode(exception), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Unexpected server error"
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String code, String message) {
        ErrorResponse response = new ErrorResponse(code, message, OffsetDateTime.now().toString());
        return ResponseEntity.status(status).body(response);
    }

    private String extractMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException methodArgumentNotValidException) {
            if (methodArgumentNotValidException.getBindingResult().getFieldError() != null) {
                return methodArgumentNotValidException.getBindingResult().getFieldError().getDefaultMessage();
            }
        }
        return exception.getMessage();
    }

    private String toCode(Exception exception) {
        String simpleName = exception.getClass().getSimpleName();
        if (simpleName.endsWith("Exception")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Exception".length());
        }
        return simpleName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
    }
}
