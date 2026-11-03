package com.couto.chefe_api.exception;

/** Editar um agendamento de antes dos menus, que não tem menu para recalcular (409). */
public class AgendamentoSemMenuException extends ConflitoException {

    public AgendamentoSemMenuException() {
        super("Agendamento antigo: cancele e faça um novo pedido");
    }
}
