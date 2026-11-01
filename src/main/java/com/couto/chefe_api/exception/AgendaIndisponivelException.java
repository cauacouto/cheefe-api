package com.couto.chefe_api.exception;

/** O horário cai num bloqueio ou dia fixo da agenda do chef (409). */
public class AgendaIndisponivelException extends ConflitoException {

    public AgendaIndisponivelException() {
        super("Chef indisponível nesse horário");
    }
}
