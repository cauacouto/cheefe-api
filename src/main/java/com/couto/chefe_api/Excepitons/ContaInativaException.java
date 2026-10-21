package com.couto.chefe_api.Excepitons;

import org.springframework.http.HttpStatus;

/**
 * A conta foi apagada (chef com ativo=false): não loga e não opera, mesmo com um token ainda válido.
 */
public class ContaInativaException extends NegocioException {

    public ContaInativaException() {
        super("conta inativa", HttpStatus.FORBIDDEN);
    }
}
