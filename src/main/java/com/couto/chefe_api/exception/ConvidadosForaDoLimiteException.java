package com.couto.chefe_api.exception;

/** A quantidade de pessoas do pedido está fora da faixa que o menu atende (409). */
public class ConvidadosForaDoLimiteException extends ConflitoException {

    public ConvidadosForaDoLimiteException(int minimo, int maximo) {
        super("Este menu atende de " + minimo + " a " + maximo + " pessoas");
    }
}
