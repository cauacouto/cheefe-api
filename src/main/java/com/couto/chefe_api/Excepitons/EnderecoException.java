package com.couto.chefe_api.Excepitons;

public class EnderecoException extends RuntimeException {


    public EnderecoException() {
        super("endereço não encontrado");
    }



        public EnderecoException(String message) {
            super(message);
        }
    }


