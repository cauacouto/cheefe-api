package com.couto.chefe_api.exception;

/** O horário cai num bloqueio ou dia fixo da agenda do chef (409). */
public class AgendaIndisponivelException extends ConflitoException {

    /** {@code nomeChef} nulo quando só um chef está em jogo; com vários, cita quem está indisponível. */
    public AgendaIndisponivelException(String nomeChef) {
        super(nomeChef == null ? "Chef indisponível nesse horário" : "Chef " + nomeChef + " indisponível nesse horário");
    }
}
