package com.couto.chefe_api.Excepitons;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("e-mail já cadastrado");
    }
}
