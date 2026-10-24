package com.couto.chefe_api.exception;

/** O bloqueio ou dia fixo pegaria agendamento confirmado: o chef desiste dele antes (409). */
public class ConfirmadoNoPeriodoException extends ConflitoException {

    public ConfirmadoNoPeriodoException(String conflitantes) {
        super("Você tem agendamento confirmado nesse período: " + conflitantes);
    }
}
