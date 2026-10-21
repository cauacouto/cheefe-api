package com.couto.chefe_api.Excepitons;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends NegocioException {

    public RecursoNaoEncontradoException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
