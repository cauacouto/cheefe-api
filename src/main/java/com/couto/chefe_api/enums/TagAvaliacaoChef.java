package com.couto.chefe_api.enums;

/** Tags que o cliente escolhe ao avaliar o chef. */
public enum TagAvaliacaoChef {
    PONTUAL("Pontual"),
    COMIDA_EXCELENTE("Comida excelente"),
    SIMPATICO("Simpático"),
    COZINHA_LIMPA("Deixou a cozinha limpa"),
    APRESENTACAO_CAPRICHADA("Apresentação caprichada"),
    ATRASOU("Atrasou"),
    COMIDA_ABAIXO_DO_ESPERADO("Comida abaixo do esperado"),
    DEIXOU_SUJEIRA("Deixou sujeira"),
    NAO_CUMPRIU_O_COMBINADO("Não cumpriu o combinado");

    private final String rotulo;

    TagAvaliacaoChef(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }
}
