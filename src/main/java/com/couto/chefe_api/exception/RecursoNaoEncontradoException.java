package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends NegocioException {

    public RecursoNaoEncontradoException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
