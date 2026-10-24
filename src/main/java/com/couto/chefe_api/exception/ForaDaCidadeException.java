package com.couto.chefe_api.exception;

/** O chef só atende na cidade de origem dele, e o endereço do agendamento é de outra cidade (409). */
public class ForaDaCidadeException extends ConflitoException {

    public ForaDaCidadeException(String chef, String cidade) {
        super("Chef " + chef + " não atende em " + cidade);
    }
}
