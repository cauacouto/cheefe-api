package com.couto.chefe_api.exception;

public class EnderecoException extends RecursoNaoEncontradoException {


    public EnderecoException() {
        super("endereço não encontrado");
    }



        public EnderecoException(String message) {
            super(message);
        }
    }


