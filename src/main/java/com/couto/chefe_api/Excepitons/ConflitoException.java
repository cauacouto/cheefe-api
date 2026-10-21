package com.couto.chefe_api.Excepitons;

import org.springframework.http.HttpStatus;

public class ConflitoException extends NegocioException {

    public ConflitoException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
