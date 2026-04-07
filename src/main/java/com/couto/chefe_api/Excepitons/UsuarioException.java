package com.couto.chefe_api.Excepitons;

public class UsuarioException extends RuntimeException {

    public UsuarioException() {
        super("usuario não encontrado");
    }

    public UsuarioException(String message) {
        super(message);
    }
}
