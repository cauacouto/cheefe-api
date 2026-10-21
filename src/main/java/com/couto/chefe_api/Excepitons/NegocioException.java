package com.couto.chefe_api.Excepitons;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base das exceções de domínio. Cada subclasse define o status HTTP que o
 * {@link GlobalExceptionHandler} devolve no {@code ProblemDetail}.
 */
@Getter
public abstract class NegocioException extends RuntimeException {

    private final HttpStatus status;

    protected NegocioException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected NegocioException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
