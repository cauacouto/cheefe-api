package com.couto.chefe_api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Média (nula abaixo de 5 avaliações), total e contagem de tags: só agregados, sem autor. */
public record ReputacaoDto(BigDecimal notaMedia, int totalAvaliacoes, List<TagContagem> tags) {

    public record TagContagem(String codigo, String rotulo, long total) {
    }
}
