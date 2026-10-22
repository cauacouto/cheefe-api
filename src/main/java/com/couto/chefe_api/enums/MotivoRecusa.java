package com.couto.chefe_api.enums;

/** Motivos do chef ao recusar um pedido ou desistir de um agendamento confirmado. */
public enum MotivoRecusa implements MotivoAgendamento {
    AGENDA_INDISPONIVEL("Não tenho agenda nesse horário"),
    LOCAL_FORA_DA_AREA("O local fica fora da minha área de atendimento"),
    NUMERO_DE_PESSOAS("Não atendo essa quantidade de pessoas"),
    IMPREVISTO("Tive um imprevisto"),
    OUTRO("Outro motivo");

    private final String rotulo;

    MotivoRecusa(String rotulo) {
        this.rotulo = rotulo;
    }

    @Override
    public String rotulo() {
        return rotulo;
    }
}
