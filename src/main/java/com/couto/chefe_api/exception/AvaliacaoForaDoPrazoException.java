package com.couto.chefe_api.exception;

/** O agendamento não está concluído, ou os 7 dias para avaliar já passaram (409). */
public class AvaliacaoForaDoPrazoException extends ConflitoException {

    private AvaliacaoForaDoPrazoException(String message) {
        super(message);
    }

    public static AvaliacaoForaDoPrazoException naoConcluido() {
        return new AvaliacaoForaDoPrazoException("Só é possível avaliar agendamentos concluídos");
    }

    public static AvaliacaoForaDoPrazoException prazoVencido() {
        return new AvaliacaoForaDoPrazoException("O prazo para avaliar (7 dias depois da conclusão) já passou");
    }
}
