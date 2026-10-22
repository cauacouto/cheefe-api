package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/** Ódio ou ameaça detectados: nada é gravado e a conta do autor foi suspensa para análise. */
public class ConteudoBloqueadoException extends NegocioException {

    public ConteudoBloqueadoException() {
        super("Conteúdo bloqueado e conta suspensa para análise da moderação", HttpStatus.FORBIDDEN);
    }
}
