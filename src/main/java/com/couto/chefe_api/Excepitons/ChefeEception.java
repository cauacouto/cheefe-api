package com.couto.chefe_api.Excepitons;

public class ChefeEception extends RuntimeException {

    public ChefeEception() {
        super("chefe não encontrado");
    }

    public ChefeEception(String message) {
        super(message);
    }
}
