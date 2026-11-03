package com.couto.chefe_api.exception;

import org.springframework.http.HttpStatus;

/** O chef só abre conversa com cliente que já atendeu (agendamento concluído): o cliente continua iniciando as dele. */
public class ConversaNaoPermitidaException extends NegocioException {

    public ConversaNaoPermitidaException() {
        super("Você só pode iniciar conversa com clientes que já atendeu", HttpStatus.FORBIDDEN);
    }
}
