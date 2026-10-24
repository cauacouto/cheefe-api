package com.couto.chefe_api.exception;

/** O telefone já está verificado em outra conta (cliente ou chef): um telefone por conta. */
public class TelefoneEmUsoException extends ConflitoException {

    public TelefoneEmUsoException() {
        super("Telefone já usado por outra conta");
    }
}
