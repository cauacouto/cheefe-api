package com.couto.chefe_api.exception;

/** A ação não vale para o status ou o horário atual do agendamento (409); a mensagem diz o porquê. */
public class AgendamentoTransicaoInvalidaException extends ConflitoException {

    public AgendamentoTransicaoInvalidaException(String message) {
        super(message);
    }
}
