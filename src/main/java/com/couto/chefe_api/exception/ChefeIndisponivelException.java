package com.couto.chefe_api.exception;

public class ChefeIndisponivelException extends ConflitoException {

    public ChefeIndisponivelException() {
        super("Um ou mais chefes não estão disponíveis ou ativos");
    }

    public ChefeIndisponivelException(String message) {
        super(message);
    }
}
