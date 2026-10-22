package com.couto.chefe_api.exception;

public class PratoNaoEncontradoException extends RecursoNaoEncontradoException {

    public PratoNaoEncontradoException() {
        super("prato não encontrado");
    }
}
