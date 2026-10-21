package com.couto.chefe_api.Excepitons;

import com.couto.chefe_api.enums.StatusAgendamento;

public class AgendamentoNaoConfirmavelException extends ConflitoException {

    public AgendamentoNaoConfirmavelException(StatusAgendamento status) {
        super("Só é possível confirmar agendamentos pendentes (status atual: " + status + ")");
    }
}
