package com.couto.chefe_api.exception;

/** O agendamento não está confirmado, ou faltam 48h ou menos para o início (409). */
public class DetalhesForaDoPrazoException extends ConflitoException {

    public DetalhesForaDoPrazoException() {
        super("Os detalhes só podem ser alterados até 48h antes");
    }
}
