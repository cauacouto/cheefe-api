package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/** O provedor não aprovou o código (errado, expirado ou tentativas esgotadas): a pessoa pede outro. */
public class CodigoInvalidoException extends NegocioException {

    public CodigoInvalidoException() {
        super("Código inválido ou expirado", HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
