package com.couto.chefe_api.Excepitons;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class RegistroTokenInvalidoException extends RuntimeException {

    public RegistroTokenInvalidoException() {
        super("cadastro pendente expirado ou inválido, faça login com o Google novamente");
    }
}
