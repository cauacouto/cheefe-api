package com.couto.chefe_api.dto;

import com.couto.chefe_api.enums.Especialidades;
import com.couto.chefe_api.enums.Senioridade;

import java.math.BigDecimal;
import java.util.UUID;


public record ChefeResponseDto(
        UUID id,
    String nome,
    String nacionalidade,
    Especialidades especialidade,
    Integer anosDeExperiencia,
    Senioridade senioridade,
    boolean disponivel,
    String idiomas,
        BigDecimal valorHora,
        String imageUrl,
        /** Nula com menos de 5 avaliações ("Novo"). */
        BigDecimal notaMedia,
        int totalAvaliacoes,
        /** Cidade de origem: o chef só atende nela. */
        String cidade,
        String estado


) {
}
