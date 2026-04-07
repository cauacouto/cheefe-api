package com.couto.chefe_api.Excepitons;

public class disponivelExcepiton extends RuntimeException {

    public disponivelExcepiton() {
        super("Um ou mais chefes não estão disponíveis ou ativos");
    }

    public disponivelExcepiton(String message) {
        super(message);
    }
}
