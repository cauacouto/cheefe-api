package com.couto.chefe_api.exception;

/** O menu está pausado ou o chef dele não aparece na busca (409). */
public class MenuIndisponivelException extends ConflitoException {

    public MenuIndisponivelException() {
        super("Menu indisponível");
    }
}
