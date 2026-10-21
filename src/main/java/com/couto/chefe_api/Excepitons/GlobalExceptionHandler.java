package com.couto.chefe_api.Excepitons;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte exceções em respostas RFC 9457 ({@link ProblemDetail}).
 *
 * <p>Exceções do Spring MVC (JSON inválido, {@code ResponseStatusException}, validação...)
 * são tratadas por {@link ResponseEntityExceptionHandler}. O handler genérico respeita
 * {@link ErrorResponse} e {@link ResponseStatus} (inclusive herdado de superclasse), e
 * devolve as exceções do Spring Security para que o {@code ExceptionTranslationFilter}
 * responda 401/403.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public record CampoInvalido(String campo, String mensagem) {
    }

    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ProblemDetail> handleNegocio(NegocioException ex) {
        if (ex.getStatus().is5xxServerError()) {
            // falha de serviço externo (ex.: storage): o cliente só vê a mensagem genérica, a causa fica no log
            log.error(ex.getMessage(), ex);
        }
        return problema(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegridade(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "A operação viola uma restrição dos dados (registro duplicado ou em uso)");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenerico(Exception ex) throws Exception {
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }

        if (ex instanceof ErrorResponse errorResponse) {
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .headers(errorResponse.getHeaders())
                    .body(errorResponse.getBody());
        }

        ResponseStatus anotacao = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(), ResponseStatus.class);
        if (anotacao != null) {
            String detalhe = StringUtils.hasText(anotacao.reason()) ? anotacao.reason() : ex.getMessage();
            return problema(anotacao.code(), detalhe);
        }

        log.error("Erro não tratado", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<CampoInvalido> campos = new ArrayList<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            campos.add(new CampoInvalido(erro.getField(), erro.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(erro -> campos.add(new CampoInvalido(erro.getObjectName(), erro.getDefaultMessage())));

        ProblemDetail body = ex.getBody();
        body.setDetail("Um ou mais campos são inválidos");
        body.setProperty("campos", campos);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatusCode status, String detalhe) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detalhe));
    }
}
