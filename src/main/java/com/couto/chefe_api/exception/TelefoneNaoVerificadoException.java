package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/**
 * Ação que exige telefone verificado (agendar, conversar, avaliar) feita por conta sem ele. O front reconhece
 * pelo {@code "codigo": "TELEFONE_NAO_VERIFICADO"} do ProblemDetail e abre a verificação por SMS.
 */
public class TelefoneNaoVerificadoException extends NegocioException {

    public static final String CODIGO = "TELEFONE_NAO_VERIFICADO";

    public TelefoneNaoVerificadoException() {
        super("Verifique seu telefone para continuar", HttpStatus.FORBIDDEN, CODIGO);
    }
}
