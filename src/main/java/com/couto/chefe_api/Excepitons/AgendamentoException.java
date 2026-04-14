package com.couto.chefe_api.Excepitons;

public class AgendamentoException extends RuntimeException {

    public AgendamentoException() {
        super("agedamento não encontrado");
    }

    public AgendamentoException(String message) {
        super(message);
    }
}
