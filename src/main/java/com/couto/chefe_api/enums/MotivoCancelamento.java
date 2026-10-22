package com.couto.chefe_api.enums;

/** Motivos do cliente ao cancelar. */
public enum MotivoCancelamento implements MotivoAgendamento {
    MUDANCA_DE_PLANOS("Mudança de planos"),
    DATA_OU_HORARIO("Preciso mudar a data ou o horário"),
    ENCONTREI_OUTRA_OPCAO("Encontrei outra opção"),
    VALOR("O valor ficou alto"),
    OUTRO("Outro motivo");

    private final String rotulo;

    MotivoCancelamento(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String rotulo() {
        return rotulo;
    }
}
