package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/** Novo pedido de SMS da mesma conta antes de passar o intervalo mínimo. */
public class PedidoDeCodigoRepetidoException extends NegocioException {

    public PedidoDeCodigoRepetidoException() {
        super("Aguarde 60 segundos para pedir outro código", HttpStatus.TOO_MANY_REQUESTS);
    }
}
