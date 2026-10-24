package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/** O telefone era de uma conta banida (hash em telefone_banido): não serve para outra conta. */
public class TelefoneBanidoException extends NegocioException {

    public TelefoneBanidoException() {
        super("Telefone não pode ser usado", HttpStatus.FORBIDDEN);
    }
}
