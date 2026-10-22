package com.couto.chefe_api.exception;

import com.couto.chefe_api.enums.StatusAgendamento;

public class AgendamentoNaoEditavelException extends ConflitoException {

    public AgendamentoNaoEditavelException(StatusAgendamento status) {
        super("Só é possível editar agendamentos pendentes (status atual: " + status + ")");
    }
}
