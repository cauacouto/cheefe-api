package com.couto.chefe_api.exception;

/** Fora do formato aceito: só celular do Brasil (DDD + 9 dígitos começando por 9, com ou sem +55). */
public class TelefoneInvalidoException extends RegraDeNegocioException {

    public TelefoneInvalidoException() {
        super("Telefone inválido");
    }
}
