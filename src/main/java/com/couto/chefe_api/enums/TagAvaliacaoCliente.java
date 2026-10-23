package com.couto.chefe_api.enums;

/** Tags que o chef escolhe ao avaliar o cliente. */
public enum TagAvaliacaoCliente {
    EDUCADO("Educado"),
    RECEPTIVO("Recebeu bem"),
    COZINHA_ORGANIZADA("Cozinha organizada"),
    PAGOU_COMO_COMBINADO("Pagou como combinado"),
    MUDOU_EM_CIMA_DA_HORA("Mudou o combinado em cima da hora"),
    DEMOROU_PARA_RECEBER("Demorou para receber"),
    COZINHA_SEM_CONDICOES("Cozinha sem condições"),
    FALTA_DE_EDUCACAO("Falta de educação");

    private final String rotulo;

    TagAvaliacaoCliente(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
