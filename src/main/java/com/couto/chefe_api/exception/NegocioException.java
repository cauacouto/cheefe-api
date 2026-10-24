package com.couto.chefe_api.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base das exceções de domínio. Cada subclasse define o status HTTP que o
 * {@link GlobalExceptionHandler} devolve no {@code ProblemDetail}.
 */
@Getter
public abstract class NegocioException extends RuntimeException {

    private final HttpStatus status;

    /** Código estável para o front decidir o que mostrar (vai no ProblemDetail como "codigo"); quase sempre nulo. */
    private final String codigo;

    protected NegocioException(String message, HttpStatus status) {
        this(message, status, (String) null);
    }

    protected NegocioException(String message, HttpStatus status, String codigo) {
        super(message);
        this.status = status;
        this.codigo = codigo;
    }

    protected NegocioException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.codigo = null;
    }
}
