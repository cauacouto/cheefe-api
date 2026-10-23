package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/**
 * A conta foi bloqueada pela moderação: não loga e a sessão aberta deixa de valer.
 */
public class ContaBloqueadaException extends NegocioException {

    public ContaBloqueadaException() {
        super("conta bloqueada", HttpStatus.FORBIDDEN);
    }
}
