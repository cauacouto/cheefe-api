package com.couto.chefe_api.exception;

public class ChefeIndisponivelException extends ConflitoException {

    public ChefeIndisponivelException() {
        super("O chef não está disponível ou ativo");
    }

    public ChefeIndisponivelException(String message) {
        super(message);
    }
}
