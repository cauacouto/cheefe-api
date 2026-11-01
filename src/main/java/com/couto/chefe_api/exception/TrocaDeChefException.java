package com.couto.chefe_api.exception;

/** Editar o pedido para o menu de outro chef (409): o agendamento tem um chef só. */
public class TrocaDeChefException extends ConflitoException {

    public TrocaDeChefException() {
        super("Para trocar de chef, cancele e faça um novo pedido");
    }
}
