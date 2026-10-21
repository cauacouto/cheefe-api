package com.couto.chefe_api.Excepitons;

public class UsuarioException extends RecursoNaoEncontradoException {

    public UsuarioException() {
        super("usuario não encontrado");
    }

    public UsuarioException(String message) {
        super(message);
    }
}
