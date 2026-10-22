package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

public class RegraDeNegocioException extends NegocioException {

    public RegraDeNegocioException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
