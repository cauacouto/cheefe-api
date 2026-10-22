package com.couto.chefe_api.exception;

public class UsuarioException extends RecursoNaoEncontradoException {

    public UsuarioException() {
        super("usuario não encontrado");
    }

    public UsuarioException(String message) {
        super(message);
    }
}
