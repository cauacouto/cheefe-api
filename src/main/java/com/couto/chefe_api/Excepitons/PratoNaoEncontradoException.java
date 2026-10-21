package com.couto.chefe_api.Excepitons;

public class PratoNaoEncontradoException extends RecursoNaoEncontradoException {

    public PratoNaoEncontradoException() {
        super("prato não encontrado");
    }
}
