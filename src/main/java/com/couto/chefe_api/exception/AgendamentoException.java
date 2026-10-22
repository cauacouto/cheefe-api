package com.couto.chefe_api.exception;

public class AgendamentoException extends RecursoNaoEncontradoException {

    public AgendamentoException() {
        super("agendamento não encontrado");
    }

    public AgendamentoException(String message) {
        super(message);
    }
}
