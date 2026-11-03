package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/** Contato em formulário (prato, menu): o autor corrige na hora, por isso recusa em vez de mascarar (400). */
public class ContatoNaoPermitidoException extends NegocioException {

    public static final String CODIGO = "CONTATO_NAO_PERMITIDO";

    /** @param onde o que foi preenchido, ex.: "prato" ou "menu" */
    public ContatoNaoPermitidoException(String onde) {
        super("Não é permitido colocar telefone, e-mail, rede social ou link no " + onde, HttpStatus.BAD_REQUEST, CODIGO);
    }
}
