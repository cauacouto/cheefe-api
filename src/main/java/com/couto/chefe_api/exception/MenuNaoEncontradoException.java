package com.couto.chefe_api.exception;

public class MenuNaoEncontradoException extends RecursoNaoEncontradoException {

    public MenuNaoEncontradoException() {
        super("menu não encontrado");
    }
}
