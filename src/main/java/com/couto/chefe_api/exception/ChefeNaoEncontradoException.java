package com.couto.chefe_api.exception;

public class ChefeNaoEncontradoException extends RecursoNaoEncontradoException {

    public ChefeNaoEncontradoException() {
        super("chefe não encontrado");
    }

    public ChefeNaoEncontradoException(String message) {
        super(message);
    }
}
